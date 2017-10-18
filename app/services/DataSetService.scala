package services

import java.{util => ju}
import javax.inject.Inject

import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import dataaccess._
import dataaccess.RepoTypes.{DataSetSettingRepo, FieldRepo, JsonCrudRepo}
import dataaccess.JsonCrudRepoExtra.InfixOps
import dataaccess.JsonUtil
import _root_.util.{GroupMapList, MessageLogger}
import com.google.inject.ImplementedBy
import models._
import Criterion.Infix
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import play.api.Configuration
import dataaccess.JsonUtil._
import _root_.util.{retry, seqFutures}
import models.FilterCondition.FilterOrId
import models.ml._

import scala.collection.Set
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

@ImplementedBy(classOf[DataSetServiceImpl])
trait DataSetService {

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

  def register(
    sourceDsa: DataSetAccessor,
    newDataSetId: String,
    newDataSetName: String,
    newStorageType: StorageType.Value,
    newDefaultDistributionFieldName: String
  ): Future[DataSetAccessor]

  def mergeDataSets(
    resultDataSetId: String,
    resultDataSetName: String,
    resultStorageType: StorageType.Value,
    dataSetIds: Seq[String],
    fieldNameMappings: Seq[Seq[String]]
  ): Future[Unit]

  def linkDataSets(
    spec: DataSetLink
  ): Future[Unit]

  def processSeriesAndSaveDataSet(
    spec: DataSetSeriesProcessingSpec
  ): Future[Unit]

  def transformSeriesAndSaveDataSet(
    spec: DataSetSeriesTransformationSpec
  ): Future[Unit]

  def loadDataAndFields(
    dsa: DataSetAccessor,
    fieldNames: Seq[String] = Nil,
    criteria: Seq[Criterion[Any]] = Nil
  ): Future[(Traversable[JsObject], Seq[Field])]

  def extractPeaks(
    series: Seq[Double],
    peakNum: Int,
    peakSelectionRatio: Option[Double]
  ): Option[Seq[Double]]
}

class DataSetServiceImpl @Inject()(
    dsaf: DataSetAccessorFactory,
    translationRepo: TranslationRepo,
    messageRepo: MessageRepo,
    rcPredictionService: RCPredictionService,
    configuration: Configuration
  ) extends DataSetService {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)
  private val dataSetIdFieldName = JsObjectIdentity.name
  private val reportLineFreq = 0.1

  private val ftf = FieldTypeHelper.fieldTypeFactory()
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
        retry(s"Data saving failed:", logger, 3)(
          dataRepo.save(transformedJsons).map { _ =>
            logProgress(startIndex + transformedJsons.size, reportLineSize, size)
            // TODO: flush??
            dataRepo.flushOps
          }
        )
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

    // helper function to parse a line and handle a parse exception by returning None
    def parse(line: String): Option[Seq[String]] =
      try {
        val values =
          if (lineBuffer.isEmpty) {
            parseLine(delimiter, line, prefixSuffixSeparators)
          } else {
            val bufferedLine = lineBuffer.mkString("") + line
            parseLine(delimiter, bufferedLine, prefixSuffixSeparators)
          }
        Some(values)
      } catch {
        case e: AdaParseException => None
      }

    // read all the lines
    contentLines.zipWithIndex.flatMap { case (line, index) =>
      // parse the line
      val values = parse(line)

      if (values.isEmpty) {
        logger.info(s"Buffered line ${index} could not be parse due to the unmatched prefix and suffix $prefixSuffixSeparators. Buffering...")
        lineBuffer.+=(line)
        Option.empty[Seq[String]]
      } else if (values.get.size < columnCount) {
        logger.info(s"Buffered line ${index} has an unexpected count '${values.get.size}' vs '${columnCount}'. Buffering...")
        lineBuffer.+=(line)
        Option.empty[Seq[String]]
      } else if (values.get.size > columnCount) {
        throw new AdaParseException(s"Buffered line ${index} has overflown an unexpected count '${values.get.size}' vs '${columnCount}'. Parsing terminated.")
      } else {
        // reset the buffer
        lineBuffer.clear()
        values
      }
    }
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

  override def mergeDataSets(
    resultDataSetId: String,
    resultDataSetName: String,
    resultStorageType: StorageType.Value,
    dataSetIds: Seq[String],
    fieldNames: Seq[Seq[String]]
  ): Future[Unit] = {
    val dsafs = dataSetIds.map(dsaf(_).get)
    val dataSetRepos = dsafs.map(_.dataSetRepo)
    val fieldRepos = dsafs.map(_.fieldRepo)
    val newFieldNames = fieldNames.map(_.head)

    for {
      // register the result data set (if not registered already)
      newDsa <- registerDerivedDataSet(dsafs.head, resultDataSetId, resultDataSetName, resultStorageType)

      newFieldRepo = newDsa.fieldRepo

      fields <- dsafs.head.fieldRepo.find(Seq("name" #-> newFieldNames))
      fieldNameMap = fields.map(field => (field.name, field)).toMap

      namedFieldTypes <- Future.sequence(
        fieldRepos.zipWithIndex.map { case (fieldRepo, index) =>
          val names = fieldNames.map(_(index))
          fieldRepo.find(Seq("name" #-> names)).map { fields =>
            val nameFieldMap = fields.map(field => (field.name, field)).toMap
            names.map(name =>
              (name, ftf(nameFieldMap.get(name).get.fieldTypeSpec))
            )
          }
        }
      )

      fieldTypesWithNewNames = newFieldNames.zip(namedFieldTypes.transpose).map { case (newFieldName, namedFieldTypes) =>
        (namedFieldTypes, newFieldName)
      }

      newFieldNameAndTypes <- inferMultiSourceFieldTypesInParallel(dataSetRepos, fieldTypesWithNewNames, 100, None)

      // delete all the new fields
      _ <- newFieldRepo.deleteAll

      // save the new fields
      _ <- {
        val newFields = newFieldNameAndTypes.map { case (fieldName, fieldType) =>
          val fieldTypeSpec = fieldType.spec
          val stringEnums = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to)})

          fieldNameMap.get(fieldName).map( field =>
            Field(name = fieldName, label = field.label, fieldType = fieldTypeSpec.fieldType, isArray = fieldTypeSpec.isArray, numValues = stringEnums)
          )
        }.flatten

        val dataSetIdEnumds = dataSetIds.zipWithIndex.map { case (dataSetId, index) => (index.toString, dataSetId) }.toMap
        val sourceDataSetIdField = Field("source_data_set_id", Some("Source Data Set Id"), FieldTypeId.Enum, false, Some(dataSetIdEnumds))

        newFieldRepo.save(newFields ++ Seq(sourceDataSetIdField))
      }

      // since we possible changed the dictionary (the data structure) we need to update the data set repo
      _ <- newDsa.updateDataSetRepo

      // get the new data set repo
      newDataRepo = newDsa.dataSetRepo

      // delete all the data
      _ <- {
        logger.info(s"Deleting all the data for '${resultDataSetId}'.")
        newDataRepo.deleteAll
      }

      // save the new items
      _ <- {
        logger.info("Saving new items")
        val newFieldNameAndTypeMap: Map[String, FieldType[_]] = newFieldNameAndTypes.toMap

        Future.sequence(
         dataSetRepos.zipWithIndex.map { case (dataSetRepo, index) =>
            val fieldNewFieldNames: Seq[(String, (FieldType[_], String))] = fieldTypesWithNewNames.map { case (fields, newFieldName) =>
              (fields(index)._1, (fields(index)._2, newFieldName))
            }
            val fieldNewFieldNameMap = fieldNewFieldNames.toMap

            dataSetRepo.find(projection = fieldNewFieldNameMap.map(_._1)).map { jsons =>
              val newJsons = jsons.map { json =>
                val newFieldValues = json.fields.map { case (fieldName, jsValue) =>
                  val (fieldType, newFieldName) = fieldNewFieldNameMap.get(fieldName).get
                  val newFieldType = newFieldNameAndTypeMap.get(newFieldName).get
                  (newFieldName, newFieldType.displayStringToJson(fieldType.jsonToDisplayString(jsValue)))
                }
                JsObject(newFieldValues ++ Seq(("source_data_set_id", JsNumber(index))))
              }
              newDataRepo.save(newJsons)
            }
          }
        )
      }
    } yield
      ()
  }

  private def inferMultiSourceFieldTypesInParallel(
    dataRepos: Seq[JsonCrudRepo],
    fieldTypesWithNewNames: Traversable[(Seq[(String, FieldType[_])], String)],
    groupSize: Int,
    jsonFieldTypeInferrer: Option[FieldTypeInferrer[JsReadable]] = None
  ): Future[Traversable[(String, FieldType[_])]] = {
    val groupedFieldTypesWithNewNames = fieldTypesWithNewNames.toSeq.grouped(groupSize).toSeq
    val jfti = jsonFieldTypeInferrer.getOrElse(jsonFti)

    def jsonToDisplayJson[T](fieldType: FieldType[T], jsValue: JsValue): JsValue =
      fieldType.jsonToValue(jsValue).map(x =>
        JsString(fieldType.valueToDisplayString(Some(x)))
      ).getOrElse(JsNull)

    for {
      fieldNameAndTypes <- Future.sequence(
        groupedFieldTypesWithNewNames.par.map { groupFields =>
          Future.sequence(
            dataRepos.zipWithIndex.map { case (dataRepo, index) =>
              val fieldNewFieldNames: Seq[(String, (FieldType[_], String))] = groupFields.map { case (fields, newFieldName) =>
                (fields(index)._1, (fields(index)._2, newFieldName))
              }

              val fieldNewFieldNameMap = fieldNewFieldNames.toMap
              dataRepo.find(projection = fieldNewFieldNames.map(_._1)).map(_.map { json =>
                val jsonFields = json.fields.map { case (fieldName, jsValue) =>
                  val fieldTypeNewFieldName = fieldNewFieldNameMap.get(fieldName).get
                  val newFieldName = fieldTypeNewFieldName._2
                  val fieldType = fieldTypeNewFieldName._1
                  (newFieldName, jsonToDisplayJson(fieldType, jsValue))
                }
                JsObject(jsonFields)
              })
            }
          ).map { jsons =>
            val newFieldNames = fieldTypesWithNewNames.map(_._2)
            inferFieldTypes(jfti, newFieldNames)(jsons.flatten)
          }
        }.toList
      )
    } yield
      fieldNameAndTypes.flatten
  }

  override def linkDataSets(
    spec: DataSetLink
  ) = {
    val leftDsa = dsaf(spec.leftSourceDataSetId).getOrElse(throw new AdaException(s"Data Set ${spec.leftSourceDataSetId} not found."))
    val rightDsa = dsaf(spec.rightSourceDataSetId).getOrElse(throw new AdaException(s"Data Set ${spec.rightSourceDataSetId} not found."))

    val leftLinkFieldNameSet = spec.linkFieldNames.map(_._1).toSet
    val rightLinkFieldNameSet = spec.linkFieldNames.map(_._2).toSet

    val leftFieldNames =
      spec.leftPreserveFieldNames match {
        case Nil => Nil
        case _ => (spec.leftPreserveFieldNames ++ leftLinkFieldNameSet).toSet
      }

    val rightFieldNames =
      spec.rightPreserveFieldNames match {
        case Nil => Nil
        case _ => (spec.rightPreserveFieldNames ++ rightLinkFieldNameSet).toSet
      }

    def fieldTypeMap(
      fieldRepo: FieldRepo,
      fieldNames: Traversable[String]
    ): Future[Map[String, FieldType[_]]] =
      for {
        fields <- fieldNames match {
          case Nil => fieldRepo.find()
          case _ => fieldRepo.find(Seq("name" #-> fieldNames.toSeq))
        }
      } yield
        fields.map( field => (field.name, ftf(field.fieldTypeSpec))).toMap

    val processingBatchSize = spec.processingBatchSize.getOrElse(20)
    val saveBatchSize = spec.saveBatchSize.getOrElse(10)

    for {
      // register the result data set (if not registered already)
      linkedDsa <- registerDerivedDataSet(leftDsa, spec.resultDataSetId, spec.resultDataSetName, spec.resultStorageType)

      // get all the left data set fields
      leftFieldTypeMap <- fieldTypeMap(leftDsa.fieldRepo, leftFieldNames)

      // get all the right data set fields
      rightFieldTypeMap <- fieldTypeMap(rightDsa.fieldRepo, rightFieldNames)

      // update the linked dictionary
      _ <- {
        val leftFieldNameAndTypes = leftFieldTypeMap.map { case (fieldName, fieldType) => (fieldName, fieldType.spec) }
        val rightFieldNameAndTypes = rightFieldTypeMap.map { case (fieldName, fieldType) => (fieldName, fieldType.spec) }.filterNot { case (fieldName, _)  => rightLinkFieldNameSet.contains(fieldName) }
        updateDictionary(spec.resultDataSetId, leftFieldNameAndTypes ++ rightFieldNameAndTypes, false, true)
      }

      // the right data set link->jsons
      linkRightJsonsMap <-
        for {
          jsons <- rightDsa.dataSetRepo.find(projection = rightFieldNames)
        } yield {
          jsons.map { json =>
            val linkAsString = spec.linkFieldNames.map { case (_, fieldName) =>
              val fieldType = rightFieldTypeMap.get(fieldName).getOrElse(
                throw new AdaException(s"Field $fieldName not found.")
              )
              fieldType.jsonToDisplayString(json \ fieldName)
            }.mkString(",")
            val strippedJson = json.fields.filterNot { case (fieldName, _)  => rightLinkFieldNameSet.contains(fieldName) }
            (linkAsString, JsObject(strippedJson))
          }.toGroupMap
        }

      // get all the left ids
      ids <- leftDsa.dataSetRepo.allIds

      // delete all items from the linked data set
      _ <- linkedDsa.dataSetRepo.deleteAll

      // link left and right data sets
      _ <-
        seqFutures(ids.toSeq.grouped(processingBatchSize)) { ids =>
          for {
            // get the jsons
            jsons <- leftDsa.dataSetRepo.findByIds(ids.head, processingBatchSize, leftFieldNames)

            // save the new linked jsons
            _ <- {
              val linkedJsons = jsons.map { json =>
                val linkAsString = spec.linkFieldNames.map { case (fieldName, _) =>
                  val fieldType = leftFieldTypeMap.get(fieldName).getOrElse(
                    throw new AdaException(s"Field $fieldName not found.")
                  )
                  fieldType.jsonToDisplayString(json \ fieldName)
                }.mkString(",")
                linkRightJsonsMap.get(linkAsString).map { rightJsons =>
                  val jsonId = (json \ JsObjectIdentity.name).as[BSONObjectID]

                  rightJsons.map { rightJson =>
                    val id = if (rightJsons.size > 1) JsObjectIdentity.next else jsonId

                    json ++ rightJson ++ Json.obj(JsObjectIdentity.name -> id)
                  }
                }.getOrElse(
                  Seq(json)
                )
              }.flatten

              linkedJsons.foreach { json =>
                val linkAsString = spec.linkFieldNames.map { case (fieldName, _) =>
                  val fieldType = leftFieldTypeMap.get(fieldName).getOrElse(
                    throw new AdaException(s"Field $fieldName not found.")
                  )
                  fieldType.jsonToDisplayString(json \ fieldName)
                }.mkString(",")
              }

              saveOrUpdateRecords(linkedDsa.dataSetRepo, linkedJsons.toSeq, None, false, None, Some(saveBatchSize))
            }
          } yield
            ()
        }
    } yield
      ()
  }

  override def processSeriesAndSaveDataSet(
    spec: DataSetSeriesProcessingSpec
  ): Future[Unit] = {
    val dsa = dsaf(spec.sourceDataSetId).getOrElse(
      throw new AdaException(s"Data set id ${spec.sourceDataSetId} not found."))

    val processingBatchSize = spec.processingBatchSize.getOrElse(20)
    val saveBatchSize = spec.core.saveBatchSize.getOrElse(5)
    val preserveFieldNameSet = spec.preserveFieldNames.toSet

    for {
      // register the result data set (if not registered already)
      newDsa <- registerDerivedDataSet(dsa, spec.resultDataSetId, spec.resultDataSetName, spec.resultStorageType)

      // get all the fields
      fields <- dsa.fieldRepo.find()

      // update the dictionary
      _ <- {
        val preservedFields = fields.filter(field => preserveFieldNameSet.contains(field.name))

        val newFields = spec.seriesProcessingSpecs.map(spec =>
          Field(spec.toString.replace('.', '_'), None, FieldTypeId.Double, true)
        )

        val fieldNameAndTypes = (preservedFields ++ newFields).map(field => (field.name, field.fieldTypeSpec))
        updateDictionary(spec.resultDataSetId, fieldNameAndTypes, false, true)
      }

      // delete all from the old data set
      _ <- newDsa.dataSetRepo.deleteAll

      // get all the ids
      ids <- dsa.dataSetRepo.allIds

      // process and save jsons
      _ <- seqFutures(ids.toSeq.grouped(processingBatchSize).zipWithIndex) {

        case (ids, groupIndex) =>
          Future.sequence(
            ids.map(dsa.dataSetRepo.get)
          ).map(_.flatten).flatMap { jsons =>

            logger.info(s"Processing series ${groupIndex * processingBatchSize} to ${(jsons.size - 1) + (groupIndex * processingBatchSize)}")
            val newJsons = jsons.par.map(processSeries(spec.seriesProcessingSpecs, preserveFieldNameSet)).toList

            // save the norm data set jsons
            saveOrUpdateRecords(newDsa.dataSetRepo, newJsons, None, false, None, Some(saveBatchSize))
          }
      }
    } yield
      ()
  }

  override def transformSeriesAndSaveDataSet(
    spec: DataSetSeriesTransformationSpec
  ) = {
    val dsa = dsaf(spec.sourceDataSetId).getOrElse(
      throw new AdaException(s"Data set id ${spec.sourceDataSetId} not found."))

    val processingBatchSize = spec.processingBatchSize.getOrElse(20)
    val saveBatchSize = spec.core.saveBatchSize.getOrElse(5)
    val preserveFieldNameSet = spec.preserveFieldNames.toSet

    for {
    // register the result data set (if not registered already)
      newDsa <- registerDerivedDataSet(dsa, spec.resultDataSetId, spec.resultDataSetName, spec.resultStorageType)

      // get all the fields
      fields <- dsa.fieldRepo.find()

      // update the dictionary
      _ <- {
        val preservedFields = fields.filter(field => preserveFieldNameSet.contains(field.name))

        val newFields = spec.seriesTransformationSpecs.map(spec =>
          Field(spec.toString.replace('.', '_'), None, FieldTypeId.Double, true)
        )

        val fieldNameAndTypes = (preservedFields ++ newFields).map(field => (field.name, field.fieldTypeSpec))
        updateDictionary(spec.resultDataSetId, fieldNameAndTypes, false, true)
      }

      // delete all from the old data set
      _ <- newDsa.dataSetRepo.deleteAll

      // get all the ids
      ids <- dsa.dataSetRepo.allIds

      // transform and save jsons
      _ <- seqFutures(ids.toSeq.grouped(processingBatchSize).zipWithIndex) {

        case (ids, groupIndex) =>
          Future.sequence(
            ids.map(dsa.dataSetRepo.get)
          ).map(_.flatten).flatMap { jsons =>

            logger.info(s"Processing series ${groupIndex * processingBatchSize} to ${(jsons.size - 1) + (groupIndex * processingBatchSize)}")

            for {
              // transform jsons
              newJsons <- Future.sequence(
                jsons.map(transformSeries(spec.seriesTransformationSpecs, preserveFieldNameSet))
              )

              // save the norm data set jsons
              _ <- saveOrUpdateRecords(newDsa.dataSetRepo, newJsons, None, false, None, Some(saveBatchSize))
            } yield
              ()
          }
      }
    } yield
      ()
  }

  private def transformSeries(
    transformationSpecs: Seq[SeriesTransformationSpec],
    preserveFieldNames: Set[String])(
    json: JsObject
  ): Future[JsObject] = {
    def transform(spec: SeriesTransformationSpec) = {
      val series = JsonUtil.traverse(json, spec.fieldPath).map(_.as[Double])

      for {
        newSeries <- rcPredictionService.transformSeries(series.map(Seq(_)), spec.transformType)
      } yield {
        val jsonSeries = newSeries.map(_.head).map(value =>
          if (value == null)
            throw new AdaException(s"Found a null value at the path ${spec.fieldPath}. \nSeries: ${series.mkString(",")}")
          else
            try {
              JsNumber(value)
            } catch {
              case e: NumberFormatException => throw new AdaException(s"Found a non-numeric value ${value} at the path ${spec.fieldPath}. \nSeries: ${series.mkString(",")}")
            }
        )

        spec.toString.replace('.', '_') -> JsArray(jsonSeries)
      }
    }

    for {
      newValues <- Future.sequence(transformationSpecs.map(transform))
    } yield {
      val preservedValues: Seq[(String, JsValue)] =
        json.fields.filter { case (fieldName, jsValue) => preserveFieldNames.contains(fieldName) }

      JsObject(preservedValues ++ newValues)
    }
  }

  private def processSeries(
    processingSpecs: Seq[SeriesProcessingSpec],
    preserveFieldNames: Set[String])(
    json: JsObject
  ): JsObject = {
    val newValues = processingSpecs.par.map { spec =>
      val series = JsonUtil.traverse(json, spec.fieldPath).map(_.as[Double])
      val newSeries: Seq[Double] = processSeries(series, spec)

      spec.toString.replace('.','_') -> JsArray(newSeries.map( value =>
        if (value == null)
          throw new AdaException(s"Found a null value at the path ${spec.fieldPath}. \nSeries: ${series.mkString(",")}")
        else
          try {
            JsNumber(value)
          } catch {
            case e: NumberFormatException => throw new AdaException(s"Found a non-numeric value ${value} at the path ${spec.fieldPath}. \nSeries: ${series.mkString(",")}")
          }
      ))
    }.toList

    val preservedValues: Seq[(String, JsValue)] =
      json.fields.filter { case (fieldName, jsValue) => preserveFieldNames.contains(fieldName)}

    JsObject(preservedValues ++ newValues)
  }

  private def processSeries(
    series: Seq[Double],
    spec: SeriesProcessingSpec
  ): Seq[Double] = {
    val newSeries = series.sliding(spec.pastValuesCount + 1).map { values =>

      def process(seq: Seq[Double]): Seq[Double] = {
        if (seq.size == 1)
          seq
        else {
          val newSeq = spec.processingType match {
            case SeriesProcessingType.Diff => values.zip(values.tail).map { case (a, b) => b - a }
            case SeriesProcessingType.RelativeDiff => values.zip(values.tail).map { case (a, b) => (b - a) / a }
            case SeriesProcessingType.Ratio => values.zip(values.tail).map { case (a, b) => b / a }
            case SeriesProcessingType.LogRatio => values.zip(values.tail).map { case (a, b) => Math.log(b / a) }
            case SeriesProcessingType.Min => Seq(values.min)
            case SeriesProcessingType.Max => Seq(values.max)
            case SeriesProcessingType.Mean => Seq(values.sum / values.size)
          }
          process(newSeq)
        }
      }

      process(values).head
    }.toSeq

    // add an initial padding if needed
    if (spec.addInitPaddingWithZeroes)
      Seq.fill(spec.pastValuesCount)(0d) ++ newSeries
    else
      newSeries
  }

  override def extractPeaks(
    series: Seq[Double],
    peakNum: Int,
    peakSelectionRatio: Option[Double]
  ): Option[Seq[Double]] = {
    val (minIndeces, maxIndeces) = localMinMaxIndeces(series)
//    val maxIndecesWithValues = maxIndeces.zip(maxIndeces.map(series(_)))

    val selectedMinIndeces =
      peakSelectionRatio.map { ratio =>
        val minIndecesWithValues = minIndeces.zip(minIndeces.map(series(_)))
        val topMins = minIndecesWithValues.sortBy(_._2).take((minIndeces.size * ratio).floor.toInt)
        topMins.map(_._1).sorted
      }.getOrElse(
        minIndeces
      )

    if (selectedMinIndeces.size > peakNum)
      Some(series.take(selectedMinIndeces(peakNum)).drop(selectedMinIndeces(0)))
    else
      None
  }

  private def localMinMaxIndeces(
    series: Seq[Double]
  ): (Seq[Int], Seq[Int]) = {
    val diffs = series.zip(series.tail).map { case (a, b) => b - a}
    val maxima = diffs.sliding(2).zipWithIndex.filter { case (diff, index) =>
      diff(0) > 0 && diff(1) <= 0
    }
    val minima = diffs.sliding(2).zipWithIndex.filter { case (diff, index) =>
      diff(0) < 0 && diff(1) >= 0
    }
    (minima.map(_._2 + 1).toSeq, maxima.map(_._2 + 1).toSeq)
  }

  private def registerDerivedDataSet(
    sourceDsa: DataSetAccessor,
    resultDataSetId: String,
    resultDataSetName: String,
    resultStorageType: StorageType.Value
  ): Future[DataSetAccessor] = {
    val metaInfoFuture = sourceDsa.metaInfo
    val settingFuture = sourceDsa.setting
    val dataViewsFuture = sourceDsa.dataViewRepo.find()

    for {
      // get the data set meta info
      metaInfo <- metaInfoFuture

      // get the data set setting
      setting <- settingFuture

      // get the data set setting
      dataViews <- dataViewsFuture

      // register the norm data set (if not registered already)
      newDsa <- dsaf.register(
        metaInfo.copy(_id = None, id = resultDataSetId, name = resultDataSetName, timeCreated = new ju.Date()),
        Some(setting.copy(_id = None, dataSetId = resultDataSetId, storageType = resultStorageType)),
        dataViews.find(_.default)
      )
    } yield
      newDsa
  }

  override def register(
    sourceDsa: DataSetAccessor,
    newDataSetId: String,
    newDataSetName: String,
    newStorageType: StorageType.Value,
    newDefaultDistributionFieldName: String
  ): Future[DataSetAccessor] = {
    for {
      // get the data set meta info
      metaInfo <- sourceDsa.metaInfo

      // register the norm data set (if not registered already)
      newDsa <- dsaf.register(
        metaInfo.copy(_id = None, id = newDataSetId, name = newDataSetName, timeCreated = new ju.Date()),
        Some(new DataSetSetting(newDataSetId, newStorageType, newDefaultDistributionFieldName)),
        None
      )
    } yield
      newDsa
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
        DataSetMetaInfo(None, newDataSetId, newDataSetName, 0, false, originalDataSetInfo.dataSpaceId),
        newDataSetSetting,
        newDataView
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
      case (json, convertedJson: JsObject) =>
        // remove null columns
        val nonNullValues =
          if (removeNullColumns) {
            json.fields.filter { case (fieldName, _) => !nullFieldNameSet.contains(fieldName) && !fieldName.equals(dataSetIdFieldName) }
          } else
            json.fields.filter { case (fieldName, _) => !fieldName.equals(dataSetIdFieldName)}

        // merge with String-converted Jsons
        JsObject(nonNullValues) ++ convertedJson
    }

    // remove all items without any content
    val finalJsons = if (removeNullRows) {
      convertedJsItems.filter(item =>
        item.fields.exists { case (fieldName, value) =>
          fieldName != dataSetIdFieldName && value != JsNull
        }
      )
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
    values: Seq[Seq[String]],
    fieldTypeInferrer: FieldTypeInferrer[String]
  ): (Seq[JsObject], Seq[(String, FieldType[_])]) = {
    val fieldTypes = values.transpose.par.map(fieldTypeInferrer.apply).toList

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

    val noCommaFtf = FieldTypeHelper.fieldTypeFactory(arrayDelimiter = ",,,")
    val noCommanFti = FieldTypeHelper.fieldTypeInferrerFactory(ftf = noCommaFtf, arrayDelimiter = ",,,").apply

    // infer new types
    val (jsons, fieldTypes) = createJsonsWithFieldTypes(
      fieldNameAndTypeSpecs.map(_._1),
      convertedStringValues.toSeq,
      noCommanFti
    )

    (jsons, fieldTypes.map(_._2.spec))
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