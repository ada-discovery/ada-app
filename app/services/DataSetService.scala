package services

import javax.inject.Inject

import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import dataaccess._
import dataaccess.RepoTypes.{DataSetSettingRepo, FieldRepo, JsonCrudRepo}
import _root_.util.{JsonUtil, MessageLogger}
import com.google.inject.ImplementedBy
import models._
import Criterion.Infix
import org.apache.commons.lang.StringUtils
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import play.api.Configuration
import _root_.util.JsonUtil._
import _root_.util.seqFutures
import models.FilterCondition.FilterOrId

import scala.collection.Set
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

@ImplementedBy(classOf[DataSetServiceImpl])
trait DataSetService {

  @Deprecated
  def inferDictionary(
    dataSetId: String,
    fieldGroupSize: Int = 150
  ): Future[Unit]

  @Deprecated
  def inferDictionary(
    fieldNames: Traversable[String],
    dataRepo: JsonCrudRepo,
    fieldRepo: FieldRepo,
    fieldGroupSize: Int,
    maxSize: Int
  ): Future[Unit]

  @Deprecated
  def inferDictionaryAndUpdateRecords(
    dataSetId: String,
    fieldGroupSize: Int,
    fieldTypeIdsToExclude: Traversable[FieldTypeId.Value] = Nil
  ): Future[Unit]

  def updateDictionary(
    dataSetId: String,
    fieldNameAndTypes: Traversable[(String, FieldTypeSpec)],
    deleteAndSave: Boolean,
    deleteNonReferenced: Boolean
  ): Future[Unit]

  def updateDictionary(
    fieldRepo: FieldRepo,
    fieldNameAndTypes: Traversable[(String, FieldTypeSpec)],
    deleteAndSave: Boolean,
    deleteNonReferenced: Boolean
  ): Future[Unit]

  def getColumnNames(
    delimiter: String,
    lineIterator: Iterator[String]
  ): Seq[String]

  def parseLines(
    columnNames: Seq[String],
    lines: Iterator[String],
    delimiter: String,
    skipFirstLine: Boolean,
    prefixSuffixSeparators: Seq[(String, String)] = Nil
  ): Iterator[Seq[String]]

  def parseLine(
    delimiter: String,
    line: String,
    prefixSuffixSeparators: Seq[(String, String)] = Nil
  ): Seq[String]

  def saveOrUpdateRecords(
    dataRepo: JsonCrudRepo,
    jsons: Seq[JsObject],
    keyField: Option[String] = None,
    updateExisting: Boolean = false,
    transformJsons: Option[Seq[JsObject] => Future[(Seq[JsObject])]] = None,
    batchSize: Option[Int] = None
  ): Future[Unit]

  def deleteRecordsExcept(
    dataRepo: JsonCrudRepo,
    keyField: String,
    keyValues: Seq[_]
  ): Future[Unit]

  def translateDataAndDictionary(
    originalDataSetId: String,
    newDataSetId: String,
    newDataSetName: String,
    newDataSetSetting: Option[DataSetSetting],
    newDataView: Option[DataView],
    useTranslations: Boolean,
    removeNullColumns: Boolean,
    removeNullRows: Boolean
  ): Future[Unit]

  def translateDataAndDictionaryOptimal(
    originalDataSetId: String,
    newDataSetId: String,
    newDataSetName: String,
    newDataSetSetting: Option[DataSetSetting],
    newDataView: Option[DataView],
    saveBatchSize: Option[Int],
    jsonFieldTypeInferrer: Option[FieldTypeInferrer[JsReadable]] = None
  ): Future[Unit]

  def loadDataAndFields(
    dsa: DataSetAccessor,
    fieldNames: Seq[String] = Nil,
    criteria: Seq[Criterion[Any]] = Nil
  ): Future[(Traversable[JsObject], Seq[Field])]
}

class DataSetServiceImpl @Inject()(
    dsaf: DataSetAccessorFactory,
    translationRepo: TranslationRepo,
    messageRepo: MessageRepo,
    configuration: Configuration
  ) extends DataSetService {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)
  private val dataSetIdFieldName = JsObjectIdentity.name
  private val reportLineFreq = 0.1

  private val ftf = FieldTypeHelper.fieldTypeFactory
  private val fti = FieldTypeHelper.fieldTypeInferrer
  private val jsonFti = FieldTypeHelper.jsonFieldTypeInferrer

  private type CreateJsonsWithFieldTypes =
    (Seq[String], Seq[Seq[String]]) => (Seq[JsObject], Seq[FieldType[_]])

  override def saveOrUpdateRecords(
    dataRepo: JsonCrudRepo,
    jsons: Seq[JsObject],
    keyField: Option[String] = None,
    updateExisting: Boolean = false,
    transformJsons: Option[Seq[JsObject] => Future[(Seq[JsObject])]] = None,
    batchSize: Option[Int] = None
  ): Future[Unit] = {
    val size = jsons.size
    val reportLineSize = size * reportLineFreq

    // helper function to transform and save given json records
    def transformAndSaveAux(
      startIndex: Int)(
      jsonRecords: Seq[JsObject]
    ): Future[Unit] = {
      val transformedJsonsFuture = transformJsons match {
        case Some(transformJsons) => transformJsons(jsonRecords)
        // if no transformation provided do nothing
        case None => Future(jsonRecords)
      }

      transformedJsonsFuture.flatMap { transformedJsons =>
        if (transformedJsons.nonEmpty) {
          logger.info(s"Saving ${transformedJsons.size} records...")
        }
//        Future.sequence(
//          transformedJsons.zipWithIndex.map { case (json, index) =>
//            dataRepo.save(json).map(_ =>
//              logProgress(startIndex + index + 1, reportLineSize, size)
//            )
//          }
//        ).map(_ => ())
        dataRepo.save(transformedJsons).map { _ =>
          logProgress(startIndex + transformedJsons.size, reportLineSize, size)
          ()
        }
      }
    }

    // helper function to transform and update given json records with key-matched existing records
    def transformAndUpdateAux(
      startIndex: Int)(
      jsonsWithIds: Seq[(JsObject, Traversable[JsValue])]
    ): Future[Unit] = {
      for {
        transformedJsonWithIds <-
          transformJsons match {
            case Some(transformJsons) =>
              for {
                transformedJsons <- transformJsons(jsonsWithIds.map(_._1))
              } yield {
                transformedJsons.zip(jsonsWithIds).map { case (transformedJson, (_, ids)) =>
                  (transformedJson, ids)
                }
              }
            // if no transformation provided do nothing
            case None => Future(jsonsWithIds)
          }
        _ <- {
          if (transformedJsonWithIds.nonEmpty) {
            logger.info(s"Updating ${transformedJsonWithIds.size} records...")
          }
          Future.sequence(
            transformedJsonWithIds.zipWithIndex.map { case ((json, ids), index) =>
              Future.sequence(
                ids.map { id =>
                  dataRepo.update(json.+(dataSetIdFieldName, id)).map(_ =>
                    logProgress(startIndex + index + 1, reportLineSize, size)
                  )
                }
              )
            }
          ).map(_.flatten)
        }
      } yield
        ()
    }

    // helper function to transform and save or update given json records
    def transformAndSaveOrUdateAux(
      startIndex: Int)(
      jsonRecords: Seq[(JsObject, JsValue)]
    ): Future[Unit] = {
      val keys = jsonRecords.map(_._2)

      val jsonsWithIdsFuture: Future[Seq[(JsObject, Traversable[JsValue])]] =
        for {
          keyIds <- dataRepo.find(
            criteria = Seq(keyField.get #-> keys),
            projection = Seq(keyField.get, dataSetIdFieldName)
          )
        } yield {
          // create a map of key-ids pairs
          val keyIdMap: Map[JsValue, Traversable[JsValue]] = keyIds.map { keyIdJson =>
            val key = (keyIdJson \ keyField.get).get
            val id = (keyIdJson \ dataSetIdFieldName).get
            (key, id)
          }.groupBy(_._1).map{ case (key, keyAndIds) => (key, keyAndIds.map(_._2))}

          jsonRecords.map { case (json, key) =>
            (json, keyIdMap.get(key).getOrElse(Nil))
          }
        }

      jsonsWithIdsFuture.flatMap { jsonsWithIds =>
        val jsonsToSave = jsonsWithIds.filter(_._2.isEmpty).map(_._1)
        val jsonsToUpdate = jsonsWithIds.filter(_._2.nonEmpty)

        for {
          _ <- if (updateExisting) {
            // update the existing records (if requested)
            transformAndUpdateAux(startIndex + jsonsToSave.size)(jsonsToUpdate)
          } else {
            if (jsonsToUpdate.nonEmpty) {
              logger.info(s"Records already exist. Skipping...")
              // otherwise do nothing... the source records are expected to be readonly
              for (index <- 0 until jsonsToUpdate.size) {
                logProgress(startIndex + jsonsToSave.size + index + 1, reportLineSize, size)
              }
            }
            Future(Nil)
          }
          // save the new records
          _ <- transformAndSaveAux(startIndex)(jsonsToSave)
        } yield ()
      }
    }

    // helper function to transform and save or update given json records
    def transformAndSaveOrUpdateMainAux(startIndex: Int)(jsonRecords: Seq[JsObject]): Future[Unit] =
      if (keyField.isDefined) {
        val jsonKeyPairs = jsonRecords.map(json => (json, (json \ keyField.get).toOption))
        val jsonsWithKeys: Seq[(JsObject, JsValue)] = jsonKeyPairs.filter(_._2.isDefined).map(x => (x._1, x._2.get))
        val jsonsWoKeys: Seq[JsObject] = jsonKeyPairs.filter(_._2.isEmpty).map(_._1)

        // if no key found transform and save
        transformAndSaveAux(startIndex)(jsonsWoKeys)

        // if key is found update or save
        transformAndSaveOrUdateAux(startIndex + jsonsWoKeys.size)(jsonsWithKeys)
      } else {
        // no key field defined, perform pure save
        transformAndSaveAux(startIndex)(jsonRecords)
      }

    ///////////////
    // Main part //
    ///////////////

    if (batchSize.isDefined) {
      val indexedGroups = jsons.grouped(batchSize.get).zipWithIndex

      indexedGroups.foldLeft(Future(())){
        case (x, (groupedJsons, groupIndex)) =>
          x.flatMap {_ =>
            transformAndSaveOrUpdateMainAux(groupIndex * batchSize.get)(groupedJsons)
          }
        }
    } else
      // save all the records
      transformAndSaveOrUpdateMainAux(0)(jsons)
  }

  override def deleteRecordsExcept(
    dataRepo: JsonCrudRepo,
    keyField: String,
    keyValues: Seq[_]
  ) =
    for {
      recordsToRemove <- dataRepo.find(
        criteria = Seq(keyField #!-> keyValues),
        projection = Seq(dataSetIdFieldName)
      )

      _ <- {
        if (recordsToRemove.nonEmpty) {
          logger.info(s"Deleting ${recordsToRemove.size} (old) records not contained in the newly imported data set.")
        }
        Future.sequence(
          recordsToRemove.map(recordToRemove =>
            dataRepo.delete((recordToRemove \ dataSetIdFieldName).get.as[BSONObjectID])
          )
        )
      }
    } yield
      ()

  override def parseLines(
    columnNames: Seq[String],
    lines: Iterator[String],
    delimiter: String,
    skipFirstLine: Boolean,
    prefixSuffixSeparators: Seq[(String, String)] = Nil
  ): Iterator[Seq[String]] = {
    val columnCount = columnNames.size

    val contentLines = if (skipFirstLine) lines.drop(1) else lines

    val lineBuffer = ListBuffer[String]()

    // read all the lines
    contentLines.zipWithIndex.map { case (line, index) =>
      // parse the line
      val values =
        if (lineBuffer.isEmpty) {
          parseLine(delimiter, line, prefixSuffixSeparators)
        } else {
          val bufferedLine = lineBuffer.mkString("") + line
          parseLine(delimiter, bufferedLine, prefixSuffixSeparators)
        }

      if (values.size < columnCount) {
        logger.info(s"Buffered line ${index} has an unexpected count '${values.size}' vs '${columnCount}'. Buffering...")
        lineBuffer.+=(line)
        Option.empty[Seq[String]]
      } else if (values.size > columnCount) {
        throw new AdaParseException(s"Buffered line ${index} has overflown an unexpected count '${values.size}' vs '${columnCount}'. Parsing terminated.")
      } else {
        // reset the buffer
        lineBuffer.clear()
        Some(values)
      }
    }.flatten
  }

  override def inferDictionary(
    dataSetId: String,
    fieldGroupSize: Int
  ): Future[Unit] = {
    logger.info(s"Dictionary inference for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo

    for {
      // get the field names
      fieldNames <- getFieldNames(dataRepo)

      // infer field types
      fieldNameAndTypes <-
        inferFieldTypesInParallel(dataRepo, fieldNames.filter(_ != dataSetIdFieldName), fieldGroupSize)

      // save, update, or delete the fields
      _ <- {
        val fieldNameAndTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}
        updateDictionary(fieldRepo, fieldNameAndTypeSpecs, false, true)
      }
    } yield
      messageLogger.info(s"Dictionary inference for data set '${dataSetId}' successfully finished.")
  }

  override def inferDictionary(
    fieldNames: Traversable[String],
    dataRepo: JsonCrudRepo,
    fieldRepo: FieldRepo,
    fieldGroupSize: Int,
    maxSize: Int
  ): Future[Unit] = {
    for {
      existingFields <- fieldRepo.find()

      // infer field types
      fieldNameAndTypes <- {
        val existingFieldNames = existingFields.map(_.name).toSet

        val remainingFieldNames = fieldNames.filterNot(existingFieldNames.contains)

        inferFieldTypesInParallel(
          dataRepo,
          remainingFieldNames.take(maxSize),
          fieldGroupSize
        )
      }

      // save, update, or delete the fields
      _ <- {
        val fieldNameAndTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}
        updateDictionary(fieldRepo, fieldNameAndTypeSpecs, false, false)
      }
    } yield
      messageLogger.info(s"Dictionary inference for data set successfully finished.")
  }

  @Deprecated
  override def inferDictionaryAndUpdateRecords(
    dataSetId: String,
    fieldGroupSize: Int,
    fieldTypeIdsToExclude: Traversable[FieldTypeId.Value]
  ): Future[Unit] = {
    logger.info(s"Dictionary inference for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo

    // helper functions to parse jsons
    def displayJsonToJson[T](fieldType: FieldType[T], json: JsReadable): JsValue = {
      val value = fieldType.displayJsonToValue(json)
      fieldType.valueToJson(value)
    }

    for {
      // get the fields to process
      fields <-
        fieldRepo.find(Seq("fieldType" #!-> fieldTypeIdsToExclude.map(_.toString).toSeq))

      // infer field types
      fieldNameAndTypes <-
        inferFieldTypesInParallel(dataRepo, fields.map(_.name), fieldGroupSize)

      // get all the items
      items <- dataRepo.find()

      // save or update the items
      _ <- {
        val newItems  = items.map { item =>
          val fieldNameJsons = fieldNameAndTypes.map { case (fieldName, fieldType) =>
            val newJson = displayJsonToJson(fieldType, (item \ fieldName))
            (fieldName, newJson)
          }
          item ++ JsObject(fieldNameJsons.toSeq)
        }

        saveOrUpdateRecords(dataRepo, newItems.toSeq, Some(dataSetIdFieldName), true)
      }

      // save, update, or delete the fields
      _ <- {
        val fieldNameAndTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}
        updateDictionary(fieldRepo, fieldNameAndTypeSpecs, false, false)
      }
    } yield
      messageLogger.info(s"Dictionary inference for data set '${dataSetId}' successfully finished.")
  }

  private def getFieldNames(dataRepo: JsonCrudRepo): Future[Set[String]] =
    for {
      records <- dataRepo.find(limit = Some(1))
    } yield
      records.headOption.map(_.keys).getOrElse(
        throw new AdaException(s"No records found. Unable to obtain field names. The associated data set might be empty.")
      )

  private def inferFieldTypesInParallel(
    dataRepo: JsonCrudRepo,
    fieldNames: Traversable[String],
    groupSize: Int,
    jsonFieldTypeInferrer: Option[FieldTypeInferrer[JsReadable]] = None
  ): Future[Traversable[(String, FieldType[_])]] = {
    val groupedFieldNames = fieldNames.toSeq.grouped(groupSize).toSeq
    val jfti = jsonFieldTypeInferrer.getOrElse(jsonFti)

    for {
      fieldNameAndTypes <- Future.sequence(
        groupedFieldNames.par.map { groupFieldNames =>
          dataRepo.find(projection = groupFieldNames).map(
            inferFieldTypes(jfti, groupFieldNames)
          )
        }.toList
      )
    } yield
      fieldNameAndTypes.flatten
  }

  private def inferFieldTypes(
    jsonFieldTypeInferrer: FieldTypeInferrer[JsReadable],
    fieldNames: Traversable[String])(
    items: Traversable[JsObject]
  ): Traversable[(String, FieldType[_])] =
    fieldNames.map { fieldName =>
      val jsons = project(items, fieldName)
      println("Inferring " + fieldName)
      (fieldName, jsonFieldTypeInferrer(jsons))
    }

  override def updateDictionary(
    dataSetId: String,
    fieldNameAndTypes: Traversable[(String, FieldTypeSpec)],
    deleteAndSave: Boolean,
    deleteNonReferenced: Boolean
  ): Future[Unit] = {
    logger.info(s"Dictionary update for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val fieldRepo = dsa.fieldRepo

    updateDictionary(fieldRepo, fieldNameAndTypes, deleteAndSave, deleteNonReferenced).map(_ =>
      messageLogger.info(s"Dictionary update for '${dataSetId}' successfully finished.")
    )
  }

  override def updateDictionary(
    fieldRepo: FieldRepo,
    fieldNameAndTypes: Traversable[(String, FieldTypeSpec)],
    deleteAndSave: Boolean,
    deleteNonReferenced: Boolean
  ): Future[Unit] = {
    val newFieldNames = fieldNameAndTypes.map(_._1).toSeq

    for {
      // get the existing fields
      referencedFields <-
        fieldRepo.find(Seq(FieldIdentity.name #-> newFieldNames))

      referencedNameFieldMap = referencedFields.map(field => (field.name, field)).toMap

      // get the non-existing fields
      nonReferencedFields <-
        fieldRepo.find(Seq(FieldIdentity.name #!-> newFieldNames))

      // fields to save or update
      fieldsToSaveAndUpdate: Traversable[Either[Field, Field]] =
        fieldNameAndTypes.map { case (fieldName, fieldType) =>

          val stringEnums = fieldType.enumValues.map(_.map { case (from, to) => (from.toString, to)})

          referencedNameFieldMap.get(fieldName) match {
            case None => Left(Field(fieldName, None, fieldType.fieldType, fieldType.isArray, stringEnums))
            case Some(field) => Right(field.copy(fieldType = fieldType.fieldType, isArray = fieldType.isArray, numValues = stringEnums))
          }
        }

      // fields to save
      fieldsToSave = fieldsToSaveAndUpdate.map(_.left.toOption).flatten

      // save the new fields
      _ <- fieldRepo.save(fieldsToSave)

      // fields to update
      fieldsToUpdate = fieldsToSaveAndUpdate.map(_.right.toOption).flatten

      // update the existing fields
      _ <- if (deleteAndSave)
          fieldRepo.delete(fieldsToUpdate.map(_.name)).flatMap { _ =>
            fieldRepo.save(fieldsToUpdate)
          }
        else
          fieldRepo.update(fieldsToUpdate)

      // remove the non-referenced fields if needed
      _ <- if (deleteNonReferenced)
          fieldRepo.delete(nonReferencedFields.map(_.name))
        else
          Future(())

    } yield
      ()
  }

  override def translateDataAndDictionary(
    originalDataSetId: String,
    newDataSetId: String,
    newDataSetName: String,
    newDataSetSetting: Option[DataSetSetting],
    newDataView: Option[DataView],
    useTranslations: Boolean,
    removeNullColumns: Boolean,
    removeNullRows: Boolean
  ) = {
    logger.info(s"Translation of the data and dictionary for data set '${originalDataSetId}' initiated.")
    val originalDsa = dsaf(originalDataSetId).get
    val originalDataRepo = originalDsa.dataSetRepo
    val originalDictionaryRepo = originalDsa.fieldRepo

    for {
      // get the accessor (data repo and field repo) for the newly registered data set
      originalDataSetInfo <- originalDsa.metaInfo
      newDsa <- dsaf.register(
        DataSetMetaInfo(None, newDataSetId, newDataSetName, 0, false, originalDataSetInfo.dataSpaceId), newDataSetSetting, newDataView
      )
      newFieldRepo = newDsa.fieldRepo

      // obtain the translation map
      translationMap <- if (useTranslations) {
          translationRepo.find().map(
            _.map(translation => (translation.original, translation.translated)).toMap
          )
        } else {
          Future(Map[String, String]())
        }

      // get the original dictionary fields
      originalFields <- originalDictionaryRepo.find()

      // get the field types
      originalFieldNameAndTypes = originalFields.map(field => (field.name, field.fieldTypeSpec)).toSeq

      // get the items (from the data set)
      items <- originalDataRepo.find(sort = Seq(AscSort(dataSetIdFieldName)))

      // transform the items and dictionary
      (newJsons, newFieldNameAndTypes) = translateDataAndDictionary(
        items, originalFieldNameAndTypes, translationMap, true, true)

      // update the dictionary
      _ <- updateDictionary(newFieldRepo, newFieldNameAndTypes, false, true)

      // since we possible changed the dictionary (the data structure) we need to update the data set repo
      _ <- newDsa.updateDataSetRepo

      // get the new data set repo
      newDataRepo = newDsa.dataSetRepo

      // delete all the data
      _ <- {
        logger.info(s"Deleting all the data for '${newDataSetId}'.")
        newDataRepo.deleteAll
      }

      // save the new items
      _ <- saveOrUpdateRecords(newDataRepo, newJsons.toSeq)
    } yield
      messageLogger.info(s"Translation of the data and dictionary for data set '${originalDataSetId}' successfully finished.")
  }

  private def createNewJsonsAndSave(
    originalDataRepo: JsonCrudRepo,
    newDataRepo: JsonCrudRepo,
    newFieldNameAndTypeMap: Map[String, FieldType[_]],
    overallSize: Int,
    batchSize: Int)(
    idsIndex: (Seq[BSONObjectID], Int)
  ): Future[Traversable[BSONObjectID]] = {
    // helper functions to parse jsons
    def displayJsonToJson[T](fieldType: FieldType[T], json: JsReadable): JsValue = {
      val value = fieldType.displayJsonToValue(json)
      fieldType.valueToJson(value)
    }

    val ids = idsIndex._1
    val index = idsIndex._2
    val reportLineSize = overallSize * reportLineFreq

    val newJsonsFuture: Future[Traversable[JsObject]] =
      originalDataRepo.find(Seq(JsObjectIdentity.name #-> ids)).map { originalItems =>
        originalItems.map { originalItem =>
          val newJsonValues = originalItem.fields.map { case (fieldName, jsonValue) =>
            val newJsonValue = newFieldNameAndTypeMap.get(fieldName) match {
              case Some(newFieldType) => displayJsonToJson(newFieldType, jsonValue)
              case None => jsonValue
            }
            (fieldName, newJsonValue)
          }
          JsObject(newJsonValues)
        }
      }
    //            ids.map { id =>
    //              originalDataRepo.get(id).map { case Some(originalItem) =>
    //
    //                val newJsonValues = originalItem.fields.map { case (fieldName, jsonValue) =>
    //                  val newJsonValue = newFieldNameAndTypeMap.get(fieldName) match {
    //                    case Some(newFieldType) => displayJsonToJson(newFieldType, jsonValue)
    //                    case None => jsonValue
    //                  }
    //                  (fieldName, newJsonValue)
    //                }
    //
    //                JsObject(newJsonValues)
    //              }
    //            }

    newJsonsFuture.flatMap { newJsons =>
      newDataRepo.save(newJsons).map { ids =>
        logProgress((index + 1) * batchSize, reportLineSize, overallSize)
        ids
      }
    }
  }

  override def translateDataAndDictionaryOptimal(
    originalDataSetId: String,
    newDataSetId: String,
    newDataSetName: String,
    newDataSetSetting: Option[DataSetSetting],
    newDataView: Option[DataView],
    saveBatchSize: Option[Int],
    jsonFieldTypeInferrer: Option[FieldTypeInferrer[JsReadable]]
  ) = {
    logger.info(s"Translation of the data and dictionary for data set '${originalDataSetId}' initiated.")
    val originalDsa = dsaf(originalDataSetId).get
    val originalDataRepo = originalDsa.dataSetRepo
    val originalDictionaryRepo = originalDsa.fieldRepo
    val originalCategoryRepo = originalDsa.categoryRepo

    for {
      // get the accessor (data repo and field repo) for the newly registered data set
      originalDataSetInfo <- originalDsa.metaInfo
      newDsa <- dsaf.register(
        DataSetMetaInfo(None, newDataSetId, newDataSetName, 0, false, originalDataSetInfo.dataSpaceId), newDataSetSetting, newDataView
      )
      newDataRepo = newDsa.dataSetRepo
      newFieldRepo = newDsa.fieldRepo
      newCategoryRepo = newDsa.categoryRepo

      // get the original dictionary fields
      originalFields <- originalDictionaryRepo.find()

      // get the original categories
      originalCategories <- originalCategoryRepo.find()

      // get the field types
      originalFieldNameAndTypes = originalFields.toSeq.map(field => (field.name, field.fieldTypeSpec))
      originalFieldNameMap = originalFields.map(field => (field.name, field)).toMap

      newFieldNameAndTypes <- {
        logger.info("Inferring new field types")
        val fieldNames = originalFieldNameAndTypes.map(_._1).sorted

        seqFutures(fieldNames.grouped(100)) {
          inferFieldTypesInParallel(originalDataRepo, _, 10, jsonFieldTypeInferrer)
        }.map(_.flatten)
      }

      // update the dictionary
//      _ <- updateDictionary(newFieldRepo, newFieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}, false, true)

      // delete all the new fields
      _ <- newFieldRepo.deleteAll

      // save the new fields
      _ <- {
        val newFields = newFieldNameAndTypes.map { case (fieldName, fieldType) =>
          val fieldTypeSpec = fieldType.spec
          val stringEnums = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to)})

          originalFieldNameMap.get(fieldName).map( field =>
            field.copy(fieldType = fieldTypeSpec.fieldType, isArray = fieldTypeSpec.isArray, numValues = stringEnums)
          )
        }.flatten
        newFieldRepo.save(newFields)
      }

      // delete all the new categories
      _ <- newCategoryRepo.deleteAll

      // save all the new categories
      _ <- newCategoryRepo.save(originalCategories)

      // since we possible changed the dictionary (the data structure) we need to update the data set repo
      _ <- newDsa.updateDataSetRepo

      // get the new data set repo
      newDataRepo = newDsa.dataSetRepo

      // delete all the data
      _ <- {
        logger.info(s"Deleting all the data for '${newDataSetId}'.")
        newDataRepo.deleteAll
      }

      originalIds <- {
        logger.info("Getting the original ids")
        // get the items (from the data set)
        originalDataRepo.find(
          projection = Seq(dataSetIdFieldName),
          sort = Seq(AscSort(dataSetIdFieldName))
        ).map(_.map(json => (json \ dataSetIdFieldName).as[BSONObjectID]))
      }

      // save the new items
      _ <- {
        logger.info("Saving new items")
        val newFieldNameAndTypeMap: Map[String, FieldType[_]] = newFieldNameAndTypes.toMap
        val size = originalIds.size
        seqFutures(
          originalIds.toSeq.grouped(saveBatchSize.getOrElse(1)).zipWithIndex
        )(
          createNewJsonsAndSave(originalDataRepo, newDataRepo, newFieldNameAndTypeMap, size, saveBatchSize.getOrElse(1))
        )
      }
    } yield
      messageLogger.info(s"Translation of the data and dictionary for data set '${originalDataSetId}' successfully finished.")
  }

  protected def translateDataAndDictionary(
    items: Traversable[JsObject],
    fieldNameAndTypes: Seq[(String, FieldTypeSpec)],
    translationMap: Map[String, String],
    removeNullColumns: Boolean,
    removeNullRows: Boolean
  ): (Traversable[JsObject], Seq[(String, FieldTypeSpec)]) = {
    val nullFieldNameSet = fieldNameAndTypes.filter(_._2.fieldType == FieldTypeId.Null).map(_._1).toSet

    // get the string or enum scalar field types
    // TODO: what about arrays?
    val stringOrEnumScalarFieldTypes = fieldNameAndTypes.filter { fieldNameAndType =>
      val fieldTypeSpec = fieldNameAndType._2
      (!fieldTypeSpec.isArray && (fieldTypeSpec.fieldType == FieldTypeId.String || fieldTypeSpec.fieldType == FieldTypeId.Enum))
    }

    // translate strings and enums
    val (convertedJsons, newFieldTypes) = translateFields(items, stringOrEnumScalarFieldTypes, translationMap)

    val convertedJsItems = (items, convertedJsons).zipped.map {
      case (json, converterJson: JsObject) =>
        // remove null columns
        val nonNullValues =
          if (removeNullColumns) {
            json.fields.filter { case (fieldName, _) => !nullFieldNameSet.contains(fieldName) && !fieldName.equals(dataSetIdFieldName) }
          } else
            json.fields.filter { case (fieldName, _) => !fieldName.equals(dataSetIdFieldName)}

        // merge with String-converted Jsons
        JsObject(nonNullValues) ++ (converterJson)
    }

    // remove all items without any content
    val finalJsons = if (removeNullRows) {
      convertedJsItems.filter(item => item.fields.exists { case (fieldName, value) =>
        fieldName != dataSetIdFieldName && value != JsNull
      })
    } else
      convertedJsItems

    // remove null columns if needed
    val nonNullFieldNameAndTypes =
      if (removeNullColumns) {
        fieldNameAndTypes.filter { case (fieldName, _) => !nullFieldNameSet.contains(fieldName)}
      } else
        fieldNameAndTypes

    // merge string and enum field name type maps
    val newFieldNameTypeMap = (stringOrEnumScalarFieldTypes.map(_._1), newFieldTypes).zipped.toMap

    // update the field types with the new ones
    val finalFieldNameAndTypes = nonNullFieldNameAndTypes.map { case (fieldName, fieldType) =>
      val newFieldType = newFieldNameTypeMap.get(fieldName).getOrElse(fieldType)
      (fieldName, newFieldType)
    }

    (finalJsons, finalFieldNameAndTypes)
  }

  protected def createJsonsWithFieldTypes(
    fieldNames: Seq[String],
    values: Seq[Seq[String]]
  ): (Seq[JsObject], Seq[(String, FieldType[_])]) = {
    val fieldTypes = values.transpose.par.map(fti.apply).toList

    val jsons = values.map( vals =>
      JsObject(
        (fieldNames, fieldTypes, vals).zipped.map {
          case (fieldName, fieldType, text) =>
            val jsonValue = fieldType.displayStringToJson(text)
            (fieldName, jsonValue)
        })
    )

    (jsons, fieldNames.zip(fieldTypes))
  }

  @Deprecated
  private def translateStringFields(
    items: Traversable[JsObject],
    stringFieldNames: Traversable[String],
    translationMap: Map[String, String]
  ): (Seq[JsObject], Seq[FieldTypeSpec]) = {

    def translate(json: JsObject) = stringFieldNames.map { fieldName =>
      val valueOption = JsonUtil.toString(json \ fieldName)
      valueOption match {
        case Some(value) => translationMap.get(value).getOrElse(value)
        case None => null
      }
    }.toSeq

    val convertedStringValues = items.map(translate)
    val (jsons, fieldTypes) = createJsonsWithFieldTypes(
      stringFieldNames.toSeq,
      convertedStringValues.toSeq
    )

    (jsons, fieldTypes.map(_._2.spec))
  }

  private def translateFields(
    items: Traversable[JsObject],
    fieldNameAndTypeSpecs: Seq[(String, FieldTypeSpec)],
    translationMap: Map[String, String]
  ): (Seq[JsObject], Seq[FieldTypeSpec]) = {

    // obtain field types from the specs
    val fieldNameAndTypes = fieldNameAndTypeSpecs.map { case (fieldName, fieldTypeSpec) =>
      (fieldName, ftf(fieldTypeSpec))
    }

    // translate jsons to String values
    def translate(json: JsObject) = fieldNameAndTypes.map { case (fieldName, fieldType) =>
      val stringValue = fieldType.jsonToDisplayString(json \ fieldName)
      translationMap.get(stringValue).getOrElse(stringValue)
    }

    val convertedStringValues = items.map(translate)

    // infer new types
    val (jsons, fieldTypes) = createJsonsWithFieldTypes(
      fieldNameAndTypeSpecs.map(_._1),
      convertedStringValues.toSeq
    )

    (jsons, fieldTypes.map(_._2.spec))
  }

  // TODO: enum inference depends of frequency, therefore we need to check the values as well
  @Deprecated
  private def translateEnumsFieldsOld(
    items: Traversable[JsObject],
    enumFieldNameAndTypes: Seq[(String, FieldTypeSpec)],
    translationMap: Map[String, String]
  ): (Seq[JsObject], Seq[FieldTypeSpec]) = {
    val newFieldNameTypeValueMaps: Seq[(String, FieldTypeSpec, Map[String, JsValue])] =
      enumFieldNameAndTypes.map { case (fieldName, enumFieldTypeSpec) =>
        enumFieldTypeSpec.enumValues.map { enumMap =>

          val newValueKeyGroupMap = enumMap.map { case (from, to) =>
            (from, translationMap.get(to).getOrElse(to))
          }.toSeq.groupBy(_._2)

          val newValueOldKeys: Seq[(String, Seq[String])] =
            newValueKeyGroupMap.map { case (value, oldKeyValues) => (value, oldKeyValues.map(_._1.toString)) }.toSeq

          val newFieldType = fti(newValueOldKeys.map(_._1))

          val oldKeyNewJsonMap: Map[String, JsValue] = newValueOldKeys.map { case (newValue, oldKeys) =>
            val newJsonValue = newFieldType.displayStringToJson(newValue)
            oldKeys.map((_, newJsonValue))
          }.flatten.toMap

          (fieldName, newFieldType.spec, oldKeyNewJsonMap)
        }
      }.flatten

    def translate(json: JsObject): JsObject = {
      val fieldJsValues = newFieldNameTypeValueMaps.map { case (fieldName, _, jsonValueMap) =>
        val originalJsValue = (json \ fieldName).get
        val valueOption = JsonUtil.toString(originalJsValue)
        val newJsonValue = valueOption match {
          case Some(value) => jsonValueMap.get(value).getOrElse(originalJsValue)
          case None => JsNull
        }
        (fieldName, newJsonValue)
      }
      JsObject(fieldJsValues)
    }

    val convertedEnumJsons = items.map(translate).toSeq
    val convertedEnumFieldSpecs = newFieldNameTypeValueMaps.map(_._2)
    (convertedEnumJsons, convertedEnumFieldSpecs)
  }

  def getColumnNames(
    delimiter: String,
    lineIterator: Iterator[String]
  ): Seq[String] =
    lineIterator.take(1).map {
      _.split(delimiter).map(columnName =>
        escapeKey(columnName.replaceAll("\"", "").trim)
      )}.flatten.toList

  // parse the line, returns the parsed items
  override def parseLine(
    delimiter: String,
    line: String,
    prefixSuffixSeparators: Seq[(String, String)] = Nil
  ): Seq[String] = {
    val itemsWithPrefixAndSuffix = line.split(delimiter, -1).map { l =>
      val trimmed = l.trim

      if (prefixSuffixSeparators.nonEmpty) {
        val (item, prefixSuffix) = handlePrefixSuffixes(trimmed, prefixSuffixSeparators)

        // TODO: this seems very ad-hoc and should be investigated where it is actually used
        val newItem = item.replaceAll("\\\\\"", "\"")

        (newItem, prefixSuffix)
      } else {
        (trimmed, None)
      }
    }

    fixImproperPrefixSuffix(delimiter, itemsWithPrefixAndSuffix)
  }

  private def handlePrefixSuffixes(
    string: String,
    prefixSuffixStrings: Seq[(String, String)]
  ): (String, Option[PrefixSuffix]) =
    prefixSuffixStrings.foldLeft((string, Option.empty[PrefixSuffix])){
      case ((string, prefixSuffix), (prefix, suffix)) =>
        if (prefixSuffix.isDefined)
          (string, prefixSuffix)
        else
          handlePrefixSuffix(string, prefix, suffix)
    }

  private def handlePrefixSuffix(
    string: String,
    prefixString: String,
    suffixString: String
  ): (String, Option[PrefixSuffix]) = {
    val prefixMatchCount = getPrefixMatchCount(string, prefixString)
    val suffixMatchCount = getSuffixMatchCount(string, suffixString)

    if (prefixMatchCount == 0 && suffixMatchCount == 0)
      (string, None)
    else {
      val prefix = prefixString * prefixMatchCount
      val suffix = suffixString * suffixMatchCount
      val expectedSuffix = suffixString * prefixMatchCount

      val item =
        if (prefix.equals(string)) {
          // the string is just prefix and suffix from the start to the end
          ""
        } else {
          string.substring(prefix.length, string.length - suffix.length).trim
        }
      (item, Some(PrefixSuffix(prefix, expectedSuffix, suffix)))
    }
  }

  private def getPrefixMatchCount(string: String, matchingString: String): Int =
    if (matchingString.isEmpty)
      0
    else
      string.grouped(matchingString.length).takeWhile(_.equals(matchingString)).size

  private def getSuffixMatchCount(string: String, matchingString: String) =
    getPrefixMatchCount(string.reverse, matchingString.reverse)

  private case class PrefixSuffix(
    prefix: String,
    expectedSuffix: String,
    suffix: String
  )

  private def fixImproperPrefixSuffix(
    delimiter: String,
    itemsWithPrefixSuffix: Array[(String, Option[PrefixSuffix])]
  ) = {
    val fixedItems = ListBuffer.empty[String]

    var unmatchedSuffixOption: Option[String] = None

    var bufferedItem = ""
    itemsWithPrefixSuffix.foreach{ case (item, prefixSuffix) =>
      val expectedSuffix = prefixSuffix.map(_.expectedSuffix).getOrElse("")
      val suffix = prefixSuffix.map(_.suffix).getOrElse("")

      unmatchedSuffixOption match {
        case None =>
          if (expectedSuffix.equals(suffix)) {
            // if we have both, prefix and suffix matching, everything is fine
            fixedItems += item
          } else {
            // prefix not matching suffix indicates an improper split, buffer
            unmatchedSuffixOption = Some(expectedSuffix)
            bufferedItem += item + delimiter
          }
        case Some(unmatchedSuffix) =>
          if (unmatchedSuffix.equals(suffix)) {
            // end buffering
            bufferedItem += item
            fixedItems += bufferedItem
            unmatchedSuffixOption = None
            bufferedItem = ""
          } else {
            // continue buffering
            bufferedItem += item + delimiter
          }
      }
    }
    if (unmatchedSuffixOption.isDefined) {
      throw new AdaParseException(s"Unmatched suffix detected ${unmatchedSuffixOption.get}.")
    }
    fixedItems
  }

  override def loadDataAndFields(
    dsa: DataSetAccessor,
    fieldNames: Seq[String],
    criteria: Seq[Criterion[Any]]
  ): Future[(Traversable[JsObject], Seq[Field])] = {
    val fieldsFuture =
      if (fieldNames.nonEmpty)
        dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))
      else
        dsa.fieldRepo.find()

    val dataFuture =
      if (fieldNames.nonEmpty)
        dsa.dataSetRepo.find(criteria, projection = fieldNames)
      else
        dsa.dataSetRepo.find(criteria)

    for {
      fields <- fieldsFuture
      jsons <- dataFuture
    } yield
      (jsons, fields.toSeq)
  }

  private def logProgress(index: Int, granularity: Double, total: Int) =
    if (index == total || (index % granularity) < ((index - 1) % granularity)) {
      val progress = if (total != 0) (index * 100) / total else 100
      val sb = new StringBuilder
      sb.append("Progress: [")
      for (_ <- 1 to progress)
        sb.append("=")
      for (_ <- 1 to 100 - progress)
        sb.append(" ")
      sb.append("]")
      logger.info(sb.toString)
    }
}