package services

import java.util.Date
import java.nio.charset.{UnsupportedCharsetException, MalformedInputException, Charset}
import javax.inject.Inject
import dataaccess.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import dataaccess._
import dataaccess.RepoTypes.{DictionaryCategoryRepo, DictionaryFieldRepo, DataSetSettingRepo, JsonCrudRepo}
import models.redcap.{Metadata, FieldType => RCFieldType}
import _root_.util.{MessageLogger, JsonUtil}
import com.google.inject.ImplementedBy
import models._
import models.synapse.{SelectColumn, ColumnType}
import Criterion.CriterionInfix
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import DictionaryCategoryRepo.saveRecursively
import play.api.Logger
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.ParSeq
import scala.concurrent.{Await, Future}
import scala.concurrent.Await._
import play.api.Configuration
import scala.concurrent.duration._
import _root_.util.JsonUtil._
import scala.io.Source
import scala.collection.{mutable, Set}
import collection.mutable.{Map => MMap}
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

@ImplementedBy(classOf[DataSetServiceImpl])
trait DataSetService {

  def importDataSetUntyped(
    dataSetImport: DataSetImport
  ): Future[Unit]

  def importDataSet(
    dataSetImport: CsvDataSetImport
  ): Future[Unit]

  def importDataSet(
    dataSetImport: SynapseDataSetImport
  ): Future[Unit]

  def importDataSet(
    dataSetImport: TranSmartDataSetImport
  ): Future[Unit]

  def importDataSet(
    dataSetImport: RedCapDataSetImport
  ): Future[Unit]

  def inferDictionary(
    dataSetId: String,
    fieldGroupSize: Int = 150
  ): Future[Unit]

  def saveOrUpdateDictionary(
    dataSetId: String,
    fieldNameAndTypes: Seq[(String, FieldTypeSpec)]
  ): Future[Unit]

  def createDummyDictionary(
    dataSetId: String
  ): Future[Unit]

  def translateDataAndDictionary(
    originalDataSetId: String,
    newDataSetMetaInfo: DataSetMetaInfo,
    newDataSetSetting: Option[DataSetSetting],
    removeNullColumns: Boolean,
    removeNullRows: Boolean
  ): Future[Unit]

  def inferFieldType(
    dataSetRepo: JsonCrudRepo,
    fieldName : String
  ): Future[FieldTypeSpec]
}

class DataSetServiceImpl @Inject()(
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dsaf: DataSetAccessorFactory,
    dataSetImportRepo: DataSetImportRepo,
    dataSetSettingRepo: DataSetSettingRepo,
    redCapServiceFactory: RedCapServiceFactory,
    synapseServiceFactory: SynapseServiceFactory,
    translationRepo: TranslationRepo,
    messageRepo: MessageRepo,
    configuration: Configuration
  ) extends DataSetService {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)
  private val dataSetIdFieldName = JsObjectIdentity.name
  private val timeout = 20 minutes
  private val defaultCharset = "UTF-8"
  private val reportLineFreq = 0.1

  private val tranSmartDelimeter = '\t'
  private val tranSmartFieldGroupSize = 100

  private val redCapChoicesDelimiter = "\\|"
  private val redCapChoiceKeyValueDelimiter = ","
  private val redCapVisitField = "redcap_event_name"
  private val redCapVisitLabel = "Visit"

  private val synapseDelimiter = ','
  private val synapseEol = "\n"
  private val synapseUsername = configuration.getString("synapse.api.username").get
  private val synapsePassword = configuration.getString("synapse.api.password").get
  private val synapseBulkDownloadAttemptNumber = 4
  private val synapseDefaultBulkDownloadGroupNumber = 5

  private val ftf = FieldTypeHelper.fieldTypeFactory
  private val fti = FieldTypeHelper.fieldTypeInferrer

  override def importDataSetUntyped(dataSetImport: DataSetImport): Future[Unit] =
    for {
      _ <- dataSetImport match {
        case x: CsvDataSetImport => importDataSet(x)
        case x: TranSmartDataSetImport => importDataSet(x)
        case x: SynapseDataSetImport => importDataSet(x)
        case x: RedCapDataSetImport => importDataSet(x)
      }
      _ <- {
        dataSetImport.timeLastExecuted = Some(new Date())
        dataSetImportRepo.update(dataSetImport)
      }
    } yield ()

  override def importDataSet(importInfo: CsvDataSetImport) =
    importLineParsableDataSet(
      importInfo,
      importInfo.delimiter,
      importInfo.eol.isDefined,
      createCsvFileLineIterator(
        importInfo.path.get,
        importInfo.charsetName,
        importInfo.eol
      ),
      Some(createJsonsWithFieldTypes)
    ).map( fieldNameAndTypes =>
      saveOrUpdateDictionary(importInfo.dataSetId, fieldNameAndTypes)
    )

  override def importDataSet(importInfo: TranSmartDataSetImport) =
    // import a data set first
    importLineParsableDataSet(
      importInfo,
      tranSmartDelimeter.toString,
      false,
      createCsvFileLineIterator(
        importInfo.dataPath.get,
        importInfo.charsetName,
        None
      ),
      Some(createJsonsWithFieldTypes)
    ).map( fieldNameAndTypes =>
      // then import a dictionary from a tranSMART mapping file
      if (importInfo.mappingPath.isDefined) {
        importTranSMARTDictionary(
          importInfo.dataSetId,
          tranSmartFieldGroupSize,
          tranSmartDelimeter.toString,
          createCsvFileLineIterator(
            importInfo.mappingPath.get,
            importInfo.charsetName,
            None
          ),
          fieldNameAndTypes
        )
      } else
        saveOrUpdateDictionary(importInfo.dataSetId, fieldNameAndTypes)
    )

  override def importDataSet(importInfo: SynapseDataSetImport) = {
    val synapseService = synapseServiceFactory(synapseUsername, synapsePassword)

    def importDataSetAux(
      transformJsons: Option[Seq[JsObject] => Future[Seq[JsObject]]],
      transformBatchSize: Option[Int]
    ) =
      importLineParsableDataSet(
        importInfo,
        synapseDelimiter.toString,
        true, {
          logger.info("Downloading CSV table from Synapse...")
          val csvFuture = synapseService.getTableAsCsv(importInfo.tableId)
          val lines = result(csvFuture, timeout).split(synapseEol)
          lines.iterator
        },
        Some(createJsonsWithFieldTypes),
        Some("ROW_ID"),
        false,
        transformJsons,
        transformBatchSize
      ).map( fieldNameAndTypes =>
        saveOrUpdateDictionary(importInfo.dataSetId, fieldNameAndTypes)
      )

    if (importInfo.downloadColumnFiles)
      for {
        // get the columns of the "file" type
        fileColumns <- synapseService.getTableColumnModels(importInfo.tableId).map(
            _.results.filter(_.columnType == ColumnType.FILEHANDLEID).map(_.toSelectColumn)
        )
        _ <- {
          val fileFieldNames = fileColumns.map { fileColumn =>
            escapeKey(fileColumn.name.replaceAll("\"", "").trim)
          }
          val fun = updateJsonsFileFields(synapseService, fileFieldNames, importInfo.tableId, importInfo.bulkDownloadGroupNumber) _
          importDataSetAux(Some(fun), importInfo.downloadRecordBatchSize)
        }
      } yield
        ()
    else
      importDataSetAux(None, None).map(_ => ())
  }

  protected def createJsonsWithFieldTypes(
    columnNames: Seq[String],
    values: Seq[Seq[String]]
  ): (Seq[JsObject], Seq[FieldTypeSpec]) = {
    val fieldTypes = values.transpose.par.map(fti.apply).toList

    val jsons = values.map( vals =>
      JsObject(
        (columnNames, fieldTypes, vals).zipped.map {
          case (fieldName, fieldType, text) =>
            val jsonValue = fieldType.displayStringToJson(text)
            (fieldName, jsonValue)
        })
    )

    (jsons, fieldTypes.map(_.spec))
  }

  private def updateJsonsFileFields(
    synapseService: SynapseService,
    fileFieldNames: Seq[String],
    tableId: String,
    bulkDownloadGroupNumber: Option[Int])(
    jsons: Seq[JsObject]
  ): Future[Seq[JsObject]] = {
    val fileHandleIds = fileFieldNames.map { fieldName =>
      jsons.map(json =>
        JsonUtil.toString(json \ fieldName)
      )
    }.flatten.flatten

    val groupNumber = bulkDownloadGroupNumber.getOrElse(synapseDefaultBulkDownloadGroupNumber)
    if (fileHandleIds.nonEmpty) {
      val groupSize = Math.max(fileHandleIds.size / groupNumber, 1)
      val groups = {
        if (fileHandleIds.size.toDouble / groupSize > groupNumber)
          fileHandleIds.grouped(groupSize + 1)
        else
          fileHandleIds.grouped(groupSize)
      }.toSeq

      logger.info(s"Bulk download of Synapse column data for ${fileHandleIds.size} file handles (split into ${groups.size} groups) initiated.")

      // download the files in a bulk
      val fileHandleIdContentsFutures = groups.par.map { groupFileHandleIds =>
        synapseService.downloadTableFilesInBulk(groupFileHandleIds, tableId, Some(synapseBulkDownloadAttemptNumber))
      }

      Future.sequence(fileHandleIdContentsFutures.toList).map(_.flatten).map { fileHandleIdContents =>
        logger.info(s"Download of ${fileHandleIdContents.size} Synapse column data finished. Updating JSONs with Synapse column data...")
        val fileHandleIdContentMap = fileHandleIdContents.toMap
        // update jsons with new file contents
        jsons.map { json =>
          val fieldNameJsons = fileFieldNames.map { fieldName =>
            JsonUtil.toString(json \ fieldName).map(fileHandleId =>
              (fieldName, Json.parse(fileHandleIdContentMap.get(fileHandleId).get))
            )
          }.flatten
          json ++ JsObject(fieldNameJsons)
        }
      }
    } else
      // no update
      Future(jsons)
  }

  override def importDataSet(
    importInfo: RedCapDataSetImport
  ) = {
    logger.info(new Date().toString)
    if (importInfo.importDictionaryFlag)
      logger.info(s"Import of data set and dictionary '${importInfo.dataSetName}' initiated.")
    else
      logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    // Red cap service to pull the data from
    val redCapService = redCapServiceFactory(importInfo.url, importInfo.token)

    // Data repo to store the data to
    val dsa = createDataSetAccessor(importInfo)
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo

    val stringFieldType = ftf(FieldTypeSpec(FieldTypeId.String)).asValueOf[String]

    // helper functions to parse jsons
    def displayJsonToJson[T](fieldType: FieldType[T], json: JsReadable): JsValue = {
      val value = fieldType.displayJsonToValue(json)
      fieldType.valueToJson(value)
    }

    def displayJsonToJsonEnum(fieldType: FieldType[_], json: JsReadable) = {
      val enumStringValue = stringFieldType.displayJsonToValue(json)
      enumStringValue match {
        case Some(string) =>
          if (ConversionUtil.isInt(string)) {
            JsNumber(string.toInt)
          } else {
            displayJsonToJson(fieldType, json)
          }
        case None => JsNull
      }
    }

    for {

      // get the data from a given red cap service
      records <- {
        logger.info("Downloading records from REDCap...")
        redCapService.listRecords("cdisc_dm_usubjd", "")
      }

      // delete all the records
      _ <- {
        logger.info(s"Deleting the old data set...")
        dataRepo.deleteAll
      }

      // import dictionary (if needed) otherwise use an existing one (should exist)
      dictionaryFieldNameTypeMap <-
        if (importInfo.importDictionaryFlag) {
          logger.info(s"RedCap dictionary inference and import for data set '${importInfo.dataSetId}' initiated.")

          val dictionaryMapFuture = importAndInferRedCapDictionary(redCapService, fieldRepo, categoryRepo, records)

          dictionaryMapFuture.map { dictionaryMap =>
            messageLogger.info(s"RedCap dictionary inference and import for data set '${importInfo.dataSetId}' successfully finished.")
            dictionaryMap
          }
        } else {
          logger.info(s"RedCap dictionary import disabled using an existing dictionary.")
          fieldRepo.find().map( fields =>
            if (fields.nonEmpty) {
              val fieldNameTypeMap: Map[String, FieldType[_]] = fields.map(field => (field.name, ftf(field.fieldTypeSpec) : FieldType[_])).toSeq.toMap
              fieldNameTypeMap
            } else {
              val message = s"No dictionary found for the data set '${importInfo.dataSetId}'. Run the REDCap data set import again with the 'import dictionary' option."
              messageLogger.error(message)
              throw new AdaException(message)
            }
          )
        }

      // get the new records
      newRecords: Traversable[JsObject] = records.map { record =>

        val newJsValues = record.fields.map { case (fieldName, jsValue) =>
          val newJsValue = dictionaryFieldNameTypeMap.get(fieldName) match {
            case Some(fieldType) => try {
              fieldType.spec.fieldType match {
                case FieldTypeId.Enum => displayJsonToJsonEnum(fieldType, jsValue)
                case _ => displayJsonToJson(fieldType, jsValue)
              }
            } catch {
              case e: Exception => throw new AdaException(s"JSON value '$jsValue' of the field '$fieldName' can not be processed.", e)
            }
            // TODO: this shouldn't be like that... if we don't have a field in the dictionary, we should discard it?
            case None => jsValue
          }
          (fieldName, newJsValue)
        }

        JsObject(newJsValues)
//        val doubleFieldType = ftf(FieldTypeSpec(FieldTypeId.Double)).asInstanceOf[FieldType[Double]]
//          // TODO: replace this adhoc rounding of age with more conceptual approach
//          val ageOption = doubleFieldType.displayJsonToValue(record \ "sv_age")
//          val newAge: JsValue = ageOption.map( age =>
//            Json.toJson(age.floor)
//          ).getOrElse(JsNull)
//          record.+(("sv_age", newAge))
      }

      // save the records (...ignore the results)
      _ <- saveAndTransformRecords(dataRepo, newRecords.toSeq, None, false, None)
    } yield
      if (importInfo.importDictionaryFlag)
        messageLogger.info(s"Import of data set and dictionary '${importInfo.dataSetName}' successfully finished.")
      else
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
  }

  protected def importAndInferRedCapDictionary(
    redCapService: RedCapService,
    fieldRepo: DictionaryFieldRepo,
    categoryRepo: DictionaryCategoryRepo,
    records: Traversable[JsObject]
  ): Future[Map[String, FieldType[_]]] = {

    def displayJsonToDisplayString[T](fieldType: FieldType[T], json: JsReadable): String = {
      val value = fieldType.displayJsonToValue(json)
      fieldType.valueToDisplayString(value)
    }

    val fieldNames = records.map(_.keys).flatten.toSet
    val stringFieldType = ftf(FieldTypeSpec(FieldTypeId.String))
    val inferredFieldNameTypeMap: Map[String, FieldType[_]] =
      fieldNames.map { fieldName =>
        val stringValues = records.map(record =>
          displayJsonToDisplayString(stringFieldType, (record \ fieldName))
        )
        (fieldName, fti(stringValues))
      }.toMap

    // TODO: optimize this... introduce field groups to speed up inference
    def inferDictionary(
      metadatas: Seq[Metadata],
      nameCategoryIdMap: Map[String, BSONObjectID]
    ): Traversable[Field] =
      metadatas.filter(metadata => inferredFieldNameTypeMap.keySet.contains(metadata.field_name)).par.map{ metadata =>
        val fieldName = metadata.field_name
        val inferredFieldType: FieldType[_] = inferredFieldNameTypeMap.get(fieldName).get
        val inferredType = inferredFieldType.spec

        def enumOrDouble: FieldTypeSpec = try {
          FieldTypeSpec(FieldTypeId.Enum, false, getEnumValues(metadata))
        } catch {
          case e: AdaParseException => {
            getDoubles(metadata)
            logger.warn(s"The field '$fieldName' has floating part(s) in the enum list and so will be treated as Double.")
            FieldTypeSpec(FieldTypeId.Double)
          }
        }

        val fieldTypeSpec = metadata.field_type match {
          case RCFieldType.radio => enumOrDouble
          case RCFieldType.checkbox => enumOrDouble
          case RCFieldType.dropdown => enumOrDouble
          case RCFieldType.calc => inferredType
          case RCFieldType.text => inferredType
          case RCFieldType.descriptive => inferredType
          case RCFieldType.yesno => FieldTypeSpec(FieldTypeId.Boolean)
          case RCFieldType.notes => inferredType
          case RCFieldType.file => inferredType
        }

        val categoryId = nameCategoryIdMap.get(metadata.form_name)
        val stringEnumValues = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to)})
        Field(metadata.field_name, fieldTypeSpec.fieldType, fieldTypeSpec.isArray, stringEnumValues, Seq[String](), Some(metadata.field_label), categoryId)
      }.toList

    for {
     // delete all the fields
      _ <- fieldRepo.deleteAll

      // delete all the categories
      _ <- categoryRepo.deleteAll

      // obtain the RedCAP metadata
      metadatas <- redCapService.listMetadatas("field_name", "")

      // save the obtained categories and return a category name with ids
      categoryNameIds <- {
        val categories = metadatas.map(_.form_name).toSet.map { formName: String =>
          new Category(formName)
        }
        categoryRepo.save(categories).map(ids =>
          (categories, ids.toSeq).zipped.map { case (category, id) =>
            (category.name, id)
          }
        )
      }

      // fields
      newFields = {
        val fields =  inferDictionary(metadatas, categoryNameIds.toMap)

        // also add redcap_event_name
        val fieldTypeSpec = inferredFieldNameTypeMap.get(redCapVisitField).get.spec
        val stringEnums = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to) })
        val visitField = Field(redCapVisitField, fieldTypeSpec.fieldType, fieldTypeSpec.isArray, stringEnums, Nil, Some(redCapVisitLabel))

        fields ++ Seq(visitField)
      }

      // save the fields
      _ <- fieldRepo.save(newFields)
    } yield
      newFields.map( field => (field.name, ftf(field.fieldTypeSpec))).toMap
  }

  private def createCsvFileLineIterator(
    path: String,
    charsetName: Option[String],
    eol: Option[String]
  ): Iterator[String] = {
    def createSource = {
      val charset = Charset.forName(charsetName.getOrElse(defaultCharset))
      Source.fromFile(path)(charset)
    }

    try {
      eol match {
        case Some(eol) => {
          // TODO: not effective... if a custom eol is used we need to read the whole file into memory and split again. It'd be better to use a custom BufferedReader
          createSource.mkString.split(eol).iterator
        }
        case None =>
          createSource.getLines
      }
    } catch {
      case e: UnsupportedCharsetException => throw AdaParseException(s"Unsupported charset '${charsetName.get}' detected.", e)
      case e: MalformedInputException => throw AdaParseException("Malformed input detected. It's most likely due to some special characters. Try a different chartset.", e)
    }
  }

  /**
    * Parses and imports data set using a provided line iterator
    *
    * @param importInfo
    * @param delimiter
    * @param skipFirstLine
    * @param createLineIterator
    * @param createJsonsWithFieldTypes
    * @param transformBatchSize
    * @return The field names and types (future)
    */
  protected def importLineParsableDataSet(
    importInfo: DataSetImport,
    delimiter: String,
    skipFirstLine: Boolean,
    createLineIterator: => Iterator[String],
    createJsonsWithFieldTypes: Option[(Seq[String], Seq[Seq[String]]) => (Seq[JsObject], Seq[FieldTypeSpec])] = None,
    keyField: Option[String] = None,
    updateExisting: Boolean = false,
    transformJsonsFun: Option[Seq[JsObject] => Future[Seq[JsObject]]] = None,
    transformBatchSize: Option[Int] = None
  ): Future[Seq[(String, FieldTypeSpec)]] = {
    logger.info(new Date().toString)
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    val dataRepo = createDataSetAccessor(importInfo).dataSetRepo

    try {
      val lines = createLineIterator
      // collect the column names
      val columnNames =  getColumnNames(delimiter, lines)

      // parse lines
      logger.info(s"Parsing lines...")
      val values = parseLines(columnNames, lines, delimiter, skipFirstLine)

      // create jsons and field types
      val createJsonsWithFieldTypesInit = createJsonsWithFieldTypes.getOrElse(defaultCreateJsonsWithFieldTypes(_,_))
      val (jsons, fieldTypes) = createJsonsWithFieldTypesInit(columnNames, values)

      for {
        // remove ALL the records from the collection if no key field defined
        _ <- if (keyField.isEmpty) {
          logger.info(s"Deleting the old data set...")
          dataRepo.deleteAll
        } else
          Future(())
        // transform jsons (if needed) and save (and update) the jsons
        _ <- {
          if (transformJsonsFun.isDefined)
            logger.info(s"Saving and transforming JSONs...")
          else
            logger.info(s"Saving JSONs...")

          saveAndTransformRecords(dataRepo, jsons, keyField, updateExisting, transformJsonsFun, transformBatchSize)
        }
        // remove the old records if key field defined
        _ <- if (keyField.isDefined) {
          removeRecords(dataRepo, jsons, keyField.get)
        } else
          Future(())

      } yield {
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
        columnNames.zip(fieldTypes)
      }
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  private def defaultCreateJsonsWithFieldTypes(
    columnNames: Seq[String],
    values: Seq[Seq[String]]
  ): (Seq[JsObject], Seq[FieldTypeSpec]) = {
    val defaultFieldTypeSpec = FieldTypeSpec(FieldTypeId.String, false)
    val fieldTypes = columnNames.map(_ => ftf(defaultFieldTypeSpec))

    val jsons = values.map( vals =>
      JsObject(
        (columnNames, vals, fieldTypes).zipped.map {
          case (columnName, value, fieldType) =>
            (columnName, fieldType.displayStringToJson(value))
        })
    )

    (jsons, fieldTypes.map(_.spec))
  }

  private def removeRecords(
    dataRepo: JsonCrudRepo,
    jsons: Seq[JsObject],
    keyField: String
  ) = {
    val newKeys = jsons.map{json => (json \ keyField).asOpt[String]}.flatten
    val recordsToRemoveFuture = dataRepo.find(
      criteria = Seq(keyField #!-> newKeys),
      projection = Seq(dataSetIdFieldName)
    )

    recordsToRemoveFuture.flatMap { recordsToRemove =>
      if (recordsToRemove.nonEmpty) {
        logger.info(s"Deleting ${recordsToRemove.size} (old) records not contained in the new data set import.")
      }
      Future.sequence(
        recordsToRemove.map( recordToRemove =>
          dataRepo.delete((recordToRemove \ dataSetIdFieldName).get.as[BSONObjectID])
        )
      )
    }
  }

  private def saveAndTransformRecords(
    dataRepo: JsonCrudRepo,
    jsons: Seq[JsObject],
    keyField: Option[String],
    updateExisting: Boolean,
    transformJsons: Option[Seq[JsObject] => Future[Seq[JsObject]]],
    transformBatchSize: Option[Int] = None
  ): Future[Unit] = {
    val size = jsons.size
    val reportLineSize = size * reportLineFreq

    // helper function to transform and save given json records
    def transformAndSaveAux(startIndex: Int)(jsonRecords: Seq[JsObject]): Future[Seq[Unit]] = {
      val transformedJsonsFuture =
        if (transformJsons.isDefined)
          transformJsons.get(jsonRecords)
        else
          // if no transformation provided do nothing
          Future(jsonRecords)

      transformedJsonsFuture.flatMap { transformedJsons =>
        if (transformedJsons.nonEmpty) {
          logger.info(s"Saving ${transformedJsons.size} records...")
        }
        Future.sequence(
          transformedJsons.zipWithIndex.map { case (json, index) =>
            dataRepo.save(json).map(_ =>
              logProgress(startIndex + index + 1, reportLineSize, size)
            )
          }
        )
      }
    }

    // helper function to transform and update given json records with key-matched existing records
    def transformAndUpdateAux(
      startIndex: Int)(
      jsonsWithIds: Seq[(JsObject, Traversable[JsValue])]
    ): Future[Seq[Unit]] = {
      val transformedJsonWithIdsFuture = if (transformJsons.isDefined) {
        for {
          transformedJsons <- transformJsons.get(jsonsWithIds.map(_._1))
        } yield
          transformedJsons.zip(jsonsWithIds).map { case (transformedJson, (_, ids)) =>
            (transformedJson, ids)
          }
      } else
        // if no transformation provided do nothing
        Future(jsonsWithIds)

      transformedJsonWithIdsFuture.flatMap { transformedJsonWithIds =>
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
            criteria = Seq(keyField.get #=> keys),
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
            transformAndUpdateAux(startIndex + jsonsToSave.size)(jsonsToUpdate).map(_ => ())
          } else {
            if (jsonsToUpdate.nonEmpty) {
              logger.info(s"Records already exist. Skipping...")
              // otherwise do nothing... the source records are expected to be readonly
              for (index <- 0 until jsonsToUpdate.size) {
                logProgress(startIndex + jsonsToSave.size + index + 1, reportLineSize, size)
              }
            }
            Future(())
          }
          // save the new records
          _ <- transformAndSaveAux(startIndex)(jsonsToSave)
        } yield
          ()
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
        transformAndSaveAux(startIndex)(jsonRecords).map(_ => ())
      }

    if (transformBatchSize.isDefined) {
      val indexedGroups = jsons.grouped(transformBatchSize.get).zipWithIndex

      indexedGroups.foldLeft(Future(())){
        case (x, (groupedJsons, groupIndex)) =>
          x.flatMap {_ =>
            transformAndSaveOrUpdateMainAux(groupIndex * transformBatchSize.get)(groupedJsons)
          }
        }
    } else
      // save all the records
      transformAndSaveOrUpdateMainAux(0)(jsons)
  }

  private def parseLines(
    columnNames: Seq[String],
    lines: Iterator[String],
    delimiter: String,
    skipFirstLine: Boolean
  ): Seq[Seq[String]] = {
    val columnCount = columnNames.size

    val contentLines = if (skipFirstLine) lines.drop(1) else lines
    var bufferedLine = ""

    // read all the lines
    contentLines.zipWithIndex.map { case (line, index) =>
      // parse the line
      bufferedLine += line
      val values = parseLine(delimiter, bufferedLine)

      if (values.size < columnCount) {
        logger.info(s"Buffered line ${index} has an unexpected count '${values.size}' vs '${columnCount}'. Buffering...")
        Option.empty[Seq[String]]
      } else if (values.size > columnCount) {
        throw new AdaParseException(s"Buffered line ${index} has overflown an unexpected count '${values.size}' vs '${columnCount}'. Parsing terminated.")
      } else {
        // reset the buffer
        bufferedLine = ""
        Some(values)
      }
    }.toSeq.flatten
  }

  private def createDataSetAccessor(importInfo: DataSetImport): DataSetAccessor =
    result(dsaf.register(
      importInfo.dataSpaceName,
      importInfo.dataSetId,
      importInfo.dataSetName,
      importInfo.setting
    ), timeout)

  override def inferDictionary(
    dataSetId: String,
    fieldGroupSize: Int
  ): Future[Unit] = {
    logger.info(s"Dictionary inference for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo

    for {
      // delete all the fields
      _ <- fieldRepo.deleteAll
      // get the field names
      fieldNames <- getFieldNames(dataRepo)
      _ <- {
        val groupedFieldNames = fieldNames.filter(_ != dataSetIdFieldName).grouped(fieldGroupSize).toSeq
        val futures = groupedFieldNames.par.map { fieldNames =>
          // Don't care about the result here, that's why we ignore ids and return Unit
          for {
            fieldNameAndTypes <- inferFieldTypes(dataRepo, fieldNames)
            fields = for ((fieldName, fieldTypeSpec) <- fieldNameAndTypes) yield {
              val stringEnums = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to) })
              Field(fieldName, fieldTypeSpec.fieldType, fieldTypeSpec.isArray, stringEnums)
            }
            _ <- fieldRepo.save(fields)
          } yield
            ()
        }
        Future.sequence(futures.toList)
      }
    } yield
      messageLogger.info(s"Dictionary inference for data set '${dataSetId}' successfully finished.")
  }

  protected def importTranSMARTDictionary(
    dataSetId: String,
    fieldGroupSize: Int,
    delimiter: String,
    mappingFileLineIterator: => Iterator[String],
    fieldNameAndTypes: Seq[(String, FieldTypeSpec)]
  ): Future[Unit] = {
    logger.info(s"TranSMART dictionary inference and import for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo

    // read the mapping file to obtain tupples: field name, field label, and category name; and a category name map
    val indexFieldNameMap: Map[Int, String] = fieldNameAndTypes.map(_._1).zipWithIndex.map(_.swap).toMap
    val (fieldNameLabelCategoryNameMap, nameCategoryMap) = createFieldNameAndCategoryNameMaps(mappingFileLineIterator, delimiter, indexFieldNameMap)

    for {
      // delete all the fields
      _ <- fieldRepo.deleteAll

      // delete all the categories
      _ <- categoryRepo.deleteAll

      // save the categories
      categoryIds: Traversable[(Category, BSONObjectID)] <- {
        val firstLayerCategories = nameCategoryMap.values.filter(!_.parent.isDefined)
        Future.sequence(
          firstLayerCategories.map(
            saveRecursively(categoryRepo, _)
          )
        ).map(_.flatten)
      }

      // save the fields... use a field label and a category from the mapping file provided, and infer a type
      _ <- {
        val categoryNameIdMap = categoryIds.map { case (category, id) => (category.name, id) }.toMap
        val groupedFieldNameAndTypes = fieldNameAndTypes.grouped(fieldGroupSize).toSeq
        val futures = groupedFieldNameAndTypes.par.map { fieldNameAndTypesGroup =>
          // Don't care about the result here, that's why we ignore ids and return Unit
          val idFutures = for ((fieldName, fieldTypeSpec) <- fieldNameAndTypesGroup) yield {
            val (fieldLabel, categoryName) = fieldNameLabelCategoryNameMap.getOrElse(fieldName, ("", None))
            val categoryId = categoryName.map(categoryNameIdMap.get(_).get)
            val stringEnums = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to)})
            fieldRepo.save(Field(fieldName, fieldTypeSpec.fieldType, fieldTypeSpec.isArray, stringEnums, Nil, Some(fieldLabel), categoryId))
          }
          Future.sequence(idFutures)
        }
        Future.sequence(futures.toList)
      }
    } yield
      messageLogger.info(s"TranSMART dictionary inference and import for data set '${dataSetId}' successfully finished.")
  }

  private def createFieldNameAndCategoryNameMaps(
    lineIterator: => Iterator[String],
    delimiter: String,
    indexFieldNameMap: Map[Int, String]
  ): (Map[String, (String, Option[String])], Map[String, Category]) = {
    val nameCategoryMap = MMap[String, Category]()

    val fieldNameLabelCategoryNameMap = lineIterator.drop(1).zipWithIndex.map { case (line, index) =>
      val values = parseLine(delimiter, line)
      if (values.size != 4)
        throw new AdaParseException(s"TranSMART mapping file contains a line (index '$index') with '${values.size}' items, but 4 expected (filename, category, column number, and data label). Parsing terminated.")

      val filename	= values(0).trim
      val categoryCD	= values(1).trim
      val colNumber	= values(2).trim.toInt
      val fieldLabel = values(3).trim

      val fieldName = indexFieldNameMap.get(colNumber - 1).getOrElse(
        throw new AdaParseException(s"TranSMART mapping file contains an invalid reference to a non-existing column '$colNumber.' Parsing terminated.")
      )

      // collect all categories
      val categoryNames = if (categoryCD.nonEmpty)
        categoryCD.split("\\+").map(_.trim.replaceAll("_", " "))
      else
        Array[String]()

      val assocCategoryName = if (categoryNames.nonEmpty) {
        val categories = categoryNames.map(categoryName =>
          nameCategoryMap.getOrElseUpdate(categoryName, new Category(categoryName))
        )
        categories.sliding(2).foreach { adjCategories =>
          val parent = adjCategories(0)
          val child = adjCategories(1)
          if (!parent.children.contains(child))
            parent.addChild(child)
        }
        Some(categoryNames.last)
      } else
        None

      (fieldName, (fieldLabel, assocCategoryName))
    }.toMap
    (fieldNameLabelCategoryNameMap, nameCategoryMap.toMap)
  }

  private def getFieldNames(dataRepo: JsonCrudRepo): Future[Set[String]] =
    for {
      records <- dataRepo.find(limit = Some(1))
    } yield
      records.headOption.map(_.keys).getOrElse(
        throw new AdaException(s"No records found. Unable to obtain field names. The associated data set might be empty.")
      )

  private def getEnumValues(metadata: Metadata) : Option[Map[Int, String]] = {
    val choices = metadata.select_choices_or_calculations.trim

    if (choices.nonEmpty) {
      try {
        val keyValueMap = choices.split(redCapChoicesDelimiter).map { choice =>
          val keyValueString = choice.split(redCapChoiceKeyValueDelimiter, 2)

          val stringKey = keyValueString(0).trim
          val value = keyValueString(1).trim

          (stringKey.toInt, value)
        }.toMap
        Some(keyValueMap)
      } catch {
        case e: NumberFormatException => throw new AdaParseException(s"RedCap Metadata '${metadata.field_name}' has non-parseable choices '${metadata.select_choices_or_calculations}'.")
      }
    } else
      None
  }

  private def getDoubles(metadata: Metadata) : Option[Traversable[Double]] = {
    val choices = metadata.select_choices_or_calculations.trim

    if (choices.nonEmpty) {
      try {
        val doubles = choices.split(redCapChoicesDelimiter).map { choice =>
          val keyValueString = choice.split(redCapChoiceKeyValueDelimiter, 2)

          val stringKey = keyValueString(0).trim
          val value = keyValueString(1).trim

          stringKey.toDouble
        }
        Some(doubles)
      } catch {
        case e: NumberFormatException => throw new AdaParseException(s"RedCap Metadata '${metadata.field_name}' has non-parseable choices '${metadata.select_choices_or_calculations}'.")
      }
    } else
      None
  }

  private def fromRedCapEnumKey(key: String): Int =
    if (!key.contains(".")) {
      // it's int
      try {
        val int = key.toInt
        //      if (int < 0)
        //        throw new AdaException(s"Negative enum key coming from REDCap found: '$key'.")
        int
      } catch {
        case _ => throw new AdaException(s"'$key' is not int.")
      }
    } else {
      // it's double :( we need to convert it to negative integer to be stay injectivee
      val double = -key.replaceAll("\\.","").toInt
      double
    }

  def inferFieldTypes(
    dataRepo: JsonCrudRepo,
    fieldNames: Traversable[String]
  ): Future[Traversable[(String, FieldTypeSpec)]] = {
    // get all the values for a given field and infer
    dataRepo.find(projection = fieldNames).map { jsons =>
      fieldNames.map { fieldName =>
        val fieldTypeSpec = inferFieldType(fieldName)(jsons)
        (fieldName, fieldTypeSpec)
      }
    }
  }

  /**
    *
    * @param dataRepo
    * @param fieldName
    * @return Tupple future : isArray (true/false) and field type id
    */
  override def inferFieldType(
    dataRepo: JsonCrudRepo,
    fieldName : String
  ): Future[FieldTypeSpec] =
    // get all the values for a given field and infer
    dataRepo.find(projection = Seq(fieldName)).map(
      inferFieldType(fieldName)
    )

  private def inferFieldType(
    fieldName: String)(
    items: Traversable[JsObject]
  ): FieldTypeSpec = {
    val arrayFlagWithValues = items.map { item =>
      val jsValue = (item \ fieldName).get
      jsValue match {
        case x: JsArray => (true, x.value.map(jsonToString(_)))
        case _ => (false, Seq(jsonToString(jsValue)))
      }
    }

    val isArray = arrayFlagWithValues.map(_._1).forall(identity)
    val values = arrayFlagWithValues.map(_._2).flatten.flatten.toSet

    val fieldTypeSpec = fti(values).spec
    fieldTypeSpec.copy(isArray = isArray)
  }

  override def saveOrUpdateDictionary(
    dataSetId: String,
    fieldNameAndTypes: Seq[(String, FieldTypeSpec)]
  ): Future[Unit] = {
    logger.info(s"Creation of dictionary for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val fieldRepo = dsa.fieldRepo
    val newFieldNames = fieldNameAndTypes.map(_._1)

    for {
      // get the existing fields
      existingFields <-
        fieldRepo.find(
          Seq(FieldIdentity.name #=> newFieldNames)
        )
      existingNameFieldMap = existingFields.map(field => (field.name, field)).toMap

      // fields to save or update
      fieldsToSaveAndUpdate: Seq[Either[Field, Field]] =
        fieldNameAndTypes.map { case (fieldName, fieldType) =>
          val stringEnums = fieldType.enumValues.map(_.map { case (from, to) => (from.toString, to)})
          existingNameFieldMap.get(fieldName) match {
            case None => Left(Field(fieldName, fieldType.fieldType, fieldType.isArray, stringEnums))
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
      _ <- fieldRepo.update(fieldsToUpdate)

      // remove the old fields
      nonExistingFields <-
        fieldRepo.find(
          Seq(FieldIdentity.name #!-> newFieldNames)
        )

      _ <- Future.sequence(
        nonExistingFields.map( field =>
          fieldRepo.delete(field.name)
        )
      )
    } yield
      messageLogger.info(s"Dictionary for data set '${dataSetId}' successfully created.")
  }

  override def createDummyDictionary(dataSetId: String): Future[Unit] = {
    logger.info(s"Creation of dummy dictionary for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val dataSetRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo

    for {
      _ <- fieldRepo.deleteAll
      fieldNames <- getFieldNames(dataSetRepo)
      _ <- {
        val fields = fieldNames.map(Field(_, FieldTypeId.String, false))
        fieldRepo.save(fields)
      }
    } yield
      messageLogger.info(s"Dummy dictionary for data set '${dataSetId}' successfully created.")
  }

  override def translateDataAndDictionary(
    originalDataSetId: String,
    newDataSetMetaInfo: DataSetMetaInfo,
    newDataSetSetting: Option[DataSetSetting],
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
        newDataSetMetaInfo.copy(dataSpaceId = originalDataSetInfo.dataSpaceId), newDataSetSetting
      )
      newDataRepo = newDsa.dataSetRepo
      newFieldRepo = newDsa.fieldRepo

      // obtain the translation map
      translationMap <- translationRepo.find().map(
        _.map(translation => (translation.original, translation.translated)).toMap
      )

      // get the original dictionary fields
      originalFields <- originalDictionaryRepo.find()

      // get the field types
      originalFieldNameAndTypes = originalFields.map(field => (field.name, field.fieldTypeSpec)).toSeq

      // get the items (from the data set)
      items <- originalDataRepo.find(sort = Seq(AscSort(dataSetIdFieldName)))

      // transform the items and dictionary
      (newJsons, newFieldNameAndTypes) = translateDataAndDictionary(
        items, originalFieldNameAndTypes, translationMap, true, true)

      // delete all the data
      _ <- {
        logger.info(s"Deleting all the data for '${newDataSetMetaInfo.id}'.")
        newDataRepo.deleteAll
      }

      // save the new items
      _ <- {
        logger.info(s"Saving ${newJsons.size} new items...")
        newDataRepo.save(newJsons)
      }

      // delete all the fields of the new data
      _ <- {
        logger.info(s"Deleting all the fields for '${newDataSetMetaInfo.id}'.")
        newFieldRepo.deleteAll
      }

      // save the new fields
      _ <- {
        val originalNameFieldMap = originalFields.map(field => (field.name, field)).toMap
        val newFields = newFieldNameAndTypes.map { case (fieldName, fieldType) =>
          val stringEnums = fieldType.enumValues.map(_.map { case (from, to) => (from.toString, to)})
          originalNameFieldMap.get(fieldName) match {
            case Some(field) => field.copy(fieldType = fieldType.fieldType, isArray = fieldType.isArray, numValues = stringEnums)
            case None => Field(fieldName, fieldType.fieldType, fieldType.isArray, stringEnums)
          }
        }
        logger.info(s"Saving ${newFields.size} new fields...")
        newFieldRepo.save(newFields)
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
    val enumFieldTypes = fieldNameAndTypes.filter(_._2.fieldType == FieldTypeId.Enum)
    val stringFieldNames = fieldNameAndTypes.filter(_._2.fieldType == FieldTypeId.String).map(_._1)

    // translate strings
    val (stringConvertedJsons, newStringFieldTypes) = translateStringFields(
      items, stringFieldNames, translationMap)

    // translate enumns
    val (enumConvertedJsons, newEnumFieldTypes) = translateEnumsFields(
      items, enumFieldTypes, translationMap)

    val convertedJsItems = (items, stringConvertedJsons, enumConvertedJsons).zipped.map {
      case (json, stringJson: JsObject, enumJson) =>
        // remove null columns
        val nonNullValues =
          if (removeNullColumns) {
            json.fields.filter { case (fieldName, _) => !nullFieldNameSet.contains(fieldName) }
          } else
            json.fields

        // merge with String-converted Jsons
        JsObject(nonNullValues) ++ (stringJson ++ enumJson)
    }

    // remove all items without any content
    val finalJsons = if (removeNullRows) {
      convertedJsItems.filter(item => item.fields.exists { case (fieldName, value) =>
        fieldName != dataSetIdFieldName && value != JsNull
      })
    } else
      convertedJsItems

    // remove the null columns if needed
    val nonNullFieldNameAndTypes =
      if (removeNullColumns) {
        fieldNameAndTypes.filter { case (fieldName, _) => !nullFieldNameSet.contains(fieldName) }
      } else
        fieldNameAndTypes

    // merge string and enum field name type maps
    val stringFieldNameTypeMap = (stringFieldNames, newStringFieldTypes).zipped.toMap
    val enumFieldNameTypeMap = (enumFieldTypes.map(_._1), newEnumFieldTypes).zipped.toMap
    val newFieldNameTypeMap = stringFieldNameTypeMap ++ enumFieldNameTypeMap

    // update the field types with the new ones
    val finalFieldNameAndTypes = nonNullFieldNameAndTypes.map { case (fieldName, fieldType) =>
      val newFieldType = newFieldNameTypeMap.get(fieldName).getOrElse(fieldType)
      (fieldName, newFieldType)
    }

    (finalJsons, finalFieldNameAndTypes)
  }

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
    createJsonsWithFieldTypes(
      stringFieldNames.toSeq,
      convertedStringValues.toSeq
    )
  }

  private def translateEnumsFields(
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

  private def jsonToString(jsValue: JsValue): Option[String] =
    jsValue match {
      case JsNull => None
      case x: JsString => Some(jsValue.as[String])
      case _ => Some(jsValue.toString)
    }

  protected def getColumnNames(delimiter: String, lineIterator: Iterator[String]) =
    lineIterator.take(1).map {
      _.split(delimiter).map(columnName =>
        escapeKey(columnName.replaceAll("\"", "").trim)
      )}.flatten.toList

  // parse the lines, returns the parsed items
  private def parseLine(delimiter: String, line: String): Seq[String] = {
    val itemsWithQuotePrefixAndSuffix = line.split(delimiter, -1).map { l =>
      val quotePrefix = getPrefix(l, '\"')
      val quoteSuffix = getSuffix(l, '\"')
      val item =
        if (quotePrefix.equals(l)) { // && quoteSuffix.equals(l)
          // the string is just quotes from the start to the end
          ""
        } else {
          l.substring(quotePrefix.size, l.size - quoteSuffix.size).trim.replaceAll("\\\\\"", "\"")
        }
//      val start = if (l.startsWith("\"")) 1 else 0
//      val end = if (l.endsWith("\"")) l.size - 1 else l.size
//      val item = if (start <= end)
//        l.substring(start, end).trim.replaceAll("\\\\\"", "\"")
//      else
//        ""
      (item, quotePrefix, quoteSuffix)
    }

    fixImproperPrefixSuffix(delimiter, itemsWithQuotePrefixAndSuffix)
  }

  def getPrefix(string: String, char: Char) =
    string.takeWhile(_.equals(char))

  def getSuffix(string: String, char: Char) =
    getPrefix(string.reverse, char)

  private def fixImproperPrefixSuffix(
    delimiter: String,
    itemsWithPrefixAndSuffix: Array[(String, String, String)]
  ) = {
    val fixedItems = ListBuffer.empty[String]

    var unmatchedPrefixOption: Option[String] = None

    var bufferedItem = ""
    itemsWithPrefixAndSuffix.foreach{ case (item, prefix, suffix) =>
      unmatchedPrefixOption match {
        case None =>
          if (prefix.equals(suffix)) {
            // if we have both, prefix and suffix matching, everything is fine
            fixedItems += item
          } else {
            // prefix not matching suffix indicates an improper split, buffer
            unmatchedPrefixOption = Some(prefix)
            bufferedItem += item + delimiter
          }
        case Some(unmatchedPrefix) =>
          if (unmatchedPrefix.equals(suffix)) {
            // end buffering
            bufferedItem += item
            fixedItems += bufferedItem
            unmatchedPrefixOption = None
            bufferedItem = ""
          } else {
            // continue buffering
            bufferedItem += item + delimiter
          }
      }
    }
    fixedItems
  }

  // fix the items that have been improperly split because the delimiter was located inside the quotes
  @Deprecated
  private def fixImproperQuotes(
    delimiter: String,
    itemsWithStartEndFlag: Array[(String, Boolean, Boolean)]
  ) = {
    val fixedItems = ListBuffer.empty[String]
    var startQuoteWithoutEnd = false
    var bufferedItem = ""
    itemsWithStartEndFlag.foreach{ case (item, startFlag, endFlag) =>
      if (!startQuoteWithoutEnd) {
        if ((startFlag && endFlag) || (!startFlag && !endFlag)) {
          // if we have both or no quotes, everything is fine
          fixedItems += item
        } else if (startFlag && !endFlag) {
          // starting quote and no ending quote indicated an improper split, buffer
          startQuoteWithoutEnd = true
          bufferedItem += item + delimiter
        } else
          throw new AdaParseException(s"Parsing failed. The item '$item' ends with a quote but there is no preceding quote to match with.")

      } else {
        if (!startFlag && !endFlag) {
          // continue buffering
          bufferedItem += item + delimiter
        } else if (!startFlag && endFlag) {
          // end buffering
          bufferedItem += item
          startQuoteWithoutEnd = false
          fixedItems += bufferedItem
        } else
          throw new AdaParseException(s"Parsing failed. The item '$item' starts with a quote but a preceding item needs an ending quote.")
      }
    }
    fixedItems
  }

  private def logProgress(index: Int, granularity: Double, total: Int) =
    if (index == total || (index % granularity) < ((index - 1) % granularity)) {
      val progress = (index * 100) / total
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