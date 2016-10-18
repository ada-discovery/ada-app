package services

import java.util.Date
import java.nio.charset.{UnsupportedCharsetException, MalformedInputException, Charset}
import javax.inject.Inject
import dataaccess.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import dataaccess._
import dataaccess.RepoTypes.DataSetSettingRepo
import models.redcap.{Metadata, FieldType => RCFieldType}
import _root_.util.{MessageLogger, JsonUtil}
import com.google.inject.ImplementedBy
import models._
import models.synapse.{SelectColumn, ColumnType}
import Criterion.CriterionInfix
import dataaccess.RepoTypes.JsonCrudRepo
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import DictionaryCategoryRepo.saveRecursively
import play.api.Logger
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
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

  def importDataSetAndDictionary(
    dataSetImport: TranSmartDataSetImport
  ): Future[Unit]

  def importDataSetAndDictionary(
    dataSetImport: RedCapDataSetImport
  ): Future[Unit]

  def inferDictionary(
    dataSetId: String,
    fieldGroupSize: Int = 150
  ): Future[Unit]

  def createDummyDictionary(
    dataSetId: String
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
        case x: TranSmartDataSetImport => importDataSetAndDictionary(x)
        case x: SynapseDataSetImport => importDataSet(x)
        case x: RedCapDataSetImport => importDataSetAndDictionary(x)
      }
      _ <- if (dataSetImport.createDummyDictionary)
        createDummyDictionary(dataSetImport.dataSetId)
      else
        Future(())
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
      createOrUpdateDictionary(importInfo.dataSetId, fieldNameAndTypes)
    )

  private def createJsonsWithFieldTypes(
    columnNames: Seq[String],
    values: Seq[Seq[String]]
  ): (Seq[JsObject], Seq[FieldTypeSpec]) = {
    val fieldTypes = values.transpose.par.map(fti.apply).toList

    val jsons = values.map( vals =>
      JsObject(
        (columnNames, fieldTypes, vals).zipped.map {
          case (columnName, fieldType, text) =>
            val jsonValue = fieldType.parseToJson(text)
            (columnName, jsonValue)
        })
    )

    (jsons, fieldTypes.map(_.spec))
  }

  override def importDataSetAndDictionary(
    importInfo: TranSmartDataSetImport
  ) = {
    // import a data set first
    val fieldNameAndTypesFuture = importLineParsableDataSet(
      importInfo,
      tranSmartDelimeter.toString,
      false,
      createCsvFileLineIterator(
        importInfo.dataPath.get,
        importInfo.charsetName,
        None
      )
    )

    // then import a dictionary from a tranSMART mapping file
    fieldNameAndTypesFuture.flatMap { fieldNameAndTypes =>
      val fieldNames =  fieldNameAndTypes.map(_._1)

      if (importInfo.mappingPath.isDefined) {
        importAndInferTranSMARTDictionary(
          importInfo.dataSetId,
          tranSmartFieldGroupSize,
          tranSmartDelimeter.toString,
          createCsvFileLineIterator(
            importInfo.mappingPath.get,
            importInfo.charsetName,
            None
          ),
          fieldNames
        )
      } else
        Future(())
    }
  }

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
        None,
        Some("ROW_ID"),
        false,
        transformJsons,
        transformBatchSize
      )

    if (importInfo.downloadColumnFiles)
      for {
        // get the columns of the "file" type
        fileColumns <- synapseService.getTableColumnModels(importInfo.tableId).map(
            _.results.filter(_.columnType == ColumnType.FILEHANDLEID).map(_.toSelectColumn)
        )
        _ <- {
          val fieldNames = fileColumns.map { fileColumn =>
            escapeKey(fileColumn.name.replaceAll("\"", "").trim)
          }
          val fun = updateJsonsFileFields(synapseService, fieldNames, importInfo.tableId, importInfo.bulkDownloadGroupNumber) _
          importDataSetAux(Some(fun), importInfo.downloadRecordBatchSize)
        }
      } yield
        ()
    else
      importDataSetAux(None, None).map(_ => ())
  }

  private def updateJsonsFileFields(
    synapseService: SynapseService,
    fieldNames: Seq[String],
    tableId: String,
    bulkDownloadGroupNumber: Option[Int])(
    jsons: Seq[JsObject]
  ): Future[Seq[JsObject]] = {
    val fileHandleIds = fieldNames.map { fieldName =>
      jsons.map(json =>
        (json \ fieldName).get.asOpt[String]
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

      val fileHandleIdContentsFutures = groups.par.map { groupFileHandleIds =>
        synapseService.downloadTableFilesInBulk(groupFileHandleIds, tableId, Some(synapseBulkDownloadAttemptNumber))
      }

      Future.sequence(fileHandleIdContentsFutures.toList).map(_.flatten).map { fileHandleIdContents =>
        logger.info(s"Download of ${fileHandleIdContents.size} Synapse column data finished. Updating JSONs with Synapse column data...")
        val fileHandleIdContentMap = fileHandleIdContents.toMap
        jsons.map { json =>
          val fieldNameJsons = fieldNames.map { fieldName =>
            (json \ fieldName).get.asOpt[String].map(fileHandleId =>
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

  override def importDataSetAndDictionary(
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
    val dataSetAccessor = createDataSetAccessor(importInfo)
    val dataRepo = dataSetAccessor.dataSetRepo

    for {
      // get the data from a given red cap service
      records <- {
        logger.info("Downloading records from REDCap...")
        redCapService.listRecords("cdisc_dm_usubjd", "")
      }
      // delete all records
      _ <- {
        logger.info(s"Deleting the old data set...")
        dataRepo.deleteAll
      }
      // save the records (...ignore the results)
      _ <- {
        logger.info(s"Saving JSONs...")
        val newRecords = records.map{ record =>
          // TODO: replace this adhoc rounding of age with more conceptual approach
          val newAge: JsValue = toDouble(record \ "sv_age").map(age =>
            Json.toJson(age.floor)
          ).getOrElse(JsNull)
          record.+(("sv_age", newAge))
        }
        Future.sequence(newRecords.map(dataRepo.save))
      }
      // import dictionary (if needed)
      _ <- if (importInfo.importDictionaryFlag)
        importAndInferRedCapDictionary(importInfo.dataSetId, redCapService, dataSetAccessor)
      else
        Future(())
    } yield
      if (importInfo.importDictionaryFlag)
        messageLogger.info(s"Import of data set and dictionary '${importInfo.dataSetName}' successfully finished.")
      else
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
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
      // parse lines and create jsons
      logger.info(s"Parsing lines...")
      val values = parseLines(columnNames, lines, delimiter, skipFirstLine)
      val (jsons, fieldTypes) = createJsonsWithFieldTypes.getOrElse(defaultCreateJsonsWithFieldTypes(_,_))(columnNames, values)

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

          saveAndTransformRecords(dataRepo, keyField, updateExisting, transformJsonsFun, transformBatchSize, jsons)
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
    val fieldTypeSpecs = columnNames.map(_ => FieldTypeSpec(FieldTypeId.String, false))

    val jsons = values.map( vals =>
      JsObject(
        (columnNames, vals).zipped.map {
          case (columnName, value) => (columnName,
            if (value.isEmpty)
              JsNull
            else
              JsString(value)
            )
        })
    )

    (jsons, fieldTypeSpecs)
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
    keyField: Option[String],
    updateExisting: Boolean,
    transformJsons: Option[Seq[JsObject] => Future[Seq[JsObject]]],
    transformBatchSize: Option[Int],
    jsons: Seq[JsObject]
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
    val fieldSyncRepo = RepoSynchronizer(fieldRepo, timeout)

    // delete all
    fieldSyncRepo.deleteAll

    val fieldNames = result(getFieldNames(dataRepo), timeout)
    val groupedFieldNames = fieldNames.filter(_ != dataSetIdFieldName).grouped(fieldGroupSize).toSeq
    val futures = groupedFieldNames.par.map { fieldNames =>
      // Don't care about the result here, that's why we ignore ids and return Unit
      for {
        fieldNameAndTypes <- inferFieldTypes(dataRepo, fieldNames)
        ids = for ((fieldName, fieldTypeSpec) <- fieldNameAndTypes) yield
          fieldRepo.save(Field(fieldName, fieldTypeSpec.fieldType, fieldTypeSpec.isArray, fieldTypeSpec.enumValues))
        _ <- Future.sequence(ids)
      } yield
        ()
    }

    Future.sequence(futures.toList).map( _ =>
      messageLogger.info(s"Dictionary inference for data set '${dataSetId}' successfully finished.")
    )
  }

  protected def importAndInferTranSMARTDictionary(
    dataSetId: String,
    fieldGroupSize: Int,
    delimiter: String,
    createMappingFileLineIterator: => Iterator[String],
    fieldNamesInOrder: Seq[String]
  ): Future[Unit] = {
    logger.info(s"TranSMART dictionary inference and import for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo
    val fieldSyncRepo = RepoSynchronizer(fieldRepo, timeout)
    val categoryRepo = dsa.categoryRepo
    val categorySyncRepo = RepoSynchronizer(categoryRepo, timeout)

    // delete all
    fieldSyncRepo.deleteAll
    categorySyncRepo.deleteAll

    val indexFieldNameMap = fieldNamesInOrder.zipWithIndex.map(_.swap).toMap
    val nameCategoryMap = MMap[String, Category]()

    val lines = createMappingFileLineIterator

    // read the mapping file to obtain tupples: field name, field label, and category name
    val fieldNameLabelCategoryNameMap = lines.drop(1).zipWithIndex.map { case (line, index) =>
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

    // save categories
    val firstLayerCategories = nameCategoryMap.values.filter(!_.parent.isDefined)
    val categoryIdsFuture: Future[Traversable[(Category, BSONObjectID)]] = Future.sequence(
      firstLayerCategories.map(
        saveRecursively(categoryRepo, _)
      )
    ).map(_.flatten)

    val categoryNameIdMapFuture = categoryIdsFuture.map(_.map{ case (category, id) => (category.name, id)}.toMap)

    // save fields... use a field label and a category from the mapping file provided, and infer a type
    val finalFuture = categoryNameIdMapFuture.flatMap{ categoryNameIdMap =>
      val groupedFieldNames = fieldNamesInOrder.grouped(fieldGroupSize).toSeq
      val futures = groupedFieldNames.par.map { fieldNames =>
        // Don't care about the result here, that's why we ignore ids and return Unit
        for {
          fieldNameAndTypes <- inferFieldTypes(dataRepo, fieldNames)
          ids = for ((fieldName, fieldTypeSpec) <- fieldNameAndTypes) yield {
            val (fieldLabel, categoryName) = fieldNameLabelCategoryNameMap.getOrElse(fieldName, ("", None))
            val categoryId = categoryName.map(categoryNameIdMap.get(_).get)
            fieldRepo.save(Field(fieldName, fieldTypeSpec.fieldType, fieldTypeSpec.isArray, fieldTypeSpec.enumValues, Nil, Some(fieldLabel), categoryId))
          }
          _ <- Future.sequence(ids)
        } yield
          ()
      }
      Future.sequence(futures.toList)
    }

    finalFuture.map( _ =>
      messageLogger.info(s"TranSMART dictionary inference and import for data set '${dataSetId}' successfully finished.")
    )
  }

  protected def importAndInferRedCapDictionary(
    dataSetId: String,
    redCapService: RedCapService,
    dsa: DataSetAccessor
  ): Future[Unit] = {
    logger.info(s"RedCap dictionary inference and import for data set '${dataSetId}' initiated.")

    val dataSetRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo

    val dictionarySyncRepo = RepoSynchronizer(fieldRepo, timeout)
    // delete all fields
    dictionarySyncRepo.deleteAll
    // delete all categories
    result(categoryRepo.deleteAll, timeout)

    // TODO: stay async.... avoid result(..)
    val metadatas = result(redCapService.listMetadatas("field_name", ""), timeout)
    val nameCategoryIdFutures = metadatas.map(_.form_name).toSet.map { formName : String =>
      val category = new Category(formName)
      categoryRepo.save(category).map(id => (category.name, id))
    }

    // categories

    val nameCategoryIdMap = result(Future.sequence(nameCategoryIdFutures), timeout).toMap

    val fieldNames = result(getFieldNames(dataSetRepo), timeout)

    // TODO: optimize this... introduce field groups to speed up inference
    val futures = metadatas.filter(metadata => fieldNames.contains(metadata.field_name)).par.map{ metadata =>
      val fieldName = metadata.field_name
      val fieldTypeFuture = inferFieldType(dataSetRepo, fieldName)
      val inferredType = result(fieldTypeFuture, timeout)

      val (fieldType, numValues) = metadata.field_type match {
        case RCFieldType.radio => (FieldTypeId.Enum, getEnumValues(metadata))
        case RCFieldType.checkbox => (FieldTypeId.Enum, getEnumValues(metadata))
        case RCFieldType.dropdown => (FieldTypeId.Enum, getEnumValues(metadata))

        case RCFieldType.calc => (inferredType, None)
        case RCFieldType.text => (inferredType, None)
        case RCFieldType.descriptive => (inferredType, None)
        case RCFieldType.yesno => (inferredType, None)
        case RCFieldType.notes => (inferredType, None)
        case RCFieldType.file => (inferredType, None)
      }

      val categoryId = nameCategoryIdMap.get(metadata.form_name)
      val field = Field(metadata.field_name, inferredType.fieldType, inferredType.isArray, numValues, Seq[String](), Some(metadata.field_label), categoryId)
      fieldRepo.save(field)
    }

    // also add redcap_event_name
    val visitFieldFuture = fieldRepo.save(Field(redCapVisitField, FieldTypeId.Enum))

    Future.sequence(futures.toList ++ Seq(visitFieldFuture)).map(_ =>
      messageLogger.info(s"RedCap dictionary inference and import for data set '${dataSetId}' successfully finished.")
    )
  }

  private def getFieldNames(dataRepo: JsonCrudRepo): Future[Set[String]] =
    for {
      records <- dataRepo.find(limit = Some(1))
    } yield
      records.headOption.map(_.keys).getOrElse(
        throw new AdaException(s"No records found. Unable to obtain field names. The associated data set might be empty.")
      )

  private def getEnumValues(metadata: Metadata) : Option[Map[String, String]] = {
    val choices = metadata.select_choices_or_calculations.trim

    if (choices.nonEmpty) {
      try {
        val keyValueMap = choices.split(redCapChoicesDelimiter).map { choice =>
          val keyValueString = choice.split(redCapChoiceKeyValueDelimiter, 2)
          (JsonUtil.escapeKey(keyValueString(0).trim), keyValueString(1).trim)
        }.toMap
        Some(keyValueMap)
      } catch {
        case e: Exception => throw new IllegalArgumentException(s"RedCap Metadata '${metadata.field_name}' has non-parseable choices '${metadata.select_choices_or_calculations}'.")
      }
    } else
      None
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

  private def createOrUpdateDictionary(
    dataSetId: String,
    fieldNameAndTypes: Seq[(String, FieldTypeSpec)]
  ): Future[Unit] = {
    logger.info(s"Creation of dictionary for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val fieldRepo = dsa.fieldRepo

    for {
      _ <- Future.sequence(
        fieldNameAndTypes.map{ case (name, fieldTypeSpec) =>
          fieldRepo.get(name).flatMap{ fieldOption =>
            fieldOption match {
              case None => fieldRepo.save(Field(name, fieldTypeSpec.fieldType, fieldTypeSpec.isArray, fieldTypeSpec.enumValues))
              case Some(field) => fieldRepo.update(field.copy(fieldType = fieldTypeSpec.fieldType, isArray = fieldTypeSpec.isArray, numValues = fieldTypeSpec.enumValues))
            }
          }
        }
      )
      fieldsToRemove <- {
        val fieldNames = fieldNameAndTypes.map(_._1)
        fieldRepo.find(
          Seq(FieldIdentity.name #!-> fieldNames)
        )
      }
      _ <- Future.sequence(
        fieldsToRemove.map( field =>
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