package services

import java.nio.charset.{MalformedInputException, Charset}
import javax.inject.Inject

import com.google.inject.ImplementedBy
import models._
import persistence.RepoSynchronizer
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import persistence.dataset.DictionaryCategoryRepo.saveRecursively
import play.api.Logger
import play.api.libs.json.{Json, JsString, JsNull, JsObject}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import util.TypeInferenceProvider
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.Await.result
import play.api.Configuration
import scala.concurrent.duration._
import util.JsonUtil._
import scala.io.Source
import scala.collection.{mutable, Set}
import collection.mutable.{Map => MMap}

@ImplementedBy(classOf[DataSetServiceImpl])
trait DataSetService {

  def importDataSet(
    importInfo: CsvDataSetImportInfo
  ): Future[Unit]

  def importDataSet(
    importInfo: SynapseDataSetImportInfo
  ): Future[Unit]

  def importDataSetAndDictionary(
    importInfo: TranSmartDataSetImportInfo,
    typeInferenceProvider: TypeInferenceProvider
  ): Future[Unit]

  def inferDictionary(
    dataSetId: String,
    typeInferenceProvider: TypeInferenceProvider,
    fieldGroupSize: Int = 150
  ): Future[Unit]

  def inferFieldType(
    dataSetRepo: JsObjectCrudRepo,
    typeInferenceProvider: TypeInferenceProvider,
    fieldName : String
  ): Future[FieldType.Value]
}

class DataSetServiceImpl @Inject()(
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dsaf: DataSetAccessorFactory,
    dataSetSettingRepo: DataSetSettingRepo,
    synapseServiceFactory: SynapseServiceFactory,
    configuration: Configuration
  ) extends DataSetService{

  private val logger = Logger
  private val timeout = 120000 millis
  private val defaultCharset = "UTF-8"
  private val reportLineFreq = 0.1
  private val tranSmartDelimeter = '\t'
  private val tranSmartFieldGroupSize = 100
  private val synapseDelimiter = ','
  private val synapseEol = "\n"
  private val synapseUsername = configuration.getString("synapse.api.username").get
  private val synapsePassword = configuration.getString("synapse.api.password").get

  override def importDataSet(importInfo: CsvDataSetImportInfo) =
    importLineParsableDataSet(
      importInfo,
      importInfo.delimiter,
      importInfo.eol.isDefined,
      createCsvFileLineIteratorAndCount(
        importInfo.path.map(Left(_)).getOrElse(Right(importInfo.file.get)),
        importInfo.charsetName,
        importInfo.eol
      )
    ).map(_ => ())

  override def importDataSetAndDictionary(
    importInfo: TranSmartDataSetImportInfo,
    typeInferenceProvider: TypeInferenceProvider
  ) = {
    // import a data set first
    val columnNamesFuture = importLineParsableDataSet(
      importInfo,
      tranSmartDelimeter.toString,
      false,
      createCsvFileLineIteratorAndCount(
        importInfo.dataPath.map(Left(_)).getOrElse(Right(importInfo.dataFile.get)),
        importInfo.charsetName,
        None
      )
    )

    columnNamesFuture.flatMap { columnNames =>
      if (importInfo.mappingPath.isDefined || importInfo.mappingFile.isDefined) {
        // then import a dictionary from a tranSMART mapping file
        importAndInferTranSMARTDictionary(
          importInfo.dataSetId,
          typeInferenceProvider,
          tranSmartFieldGroupSize,
          tranSmartDelimeter.toString,
          createCsvFileLineIteratorAndCount(
            importInfo.mappingPath.map(Left(_)).getOrElse(Right(importInfo.mappingFile.get)),
            importInfo.charsetName,
            None
          ),
          columnNames
        )
      } else
        Future(())
    }
  }

  override def importDataSet(importInfo: SynapseDataSetImportInfo) =
    importLineParsableDataSet(
      importInfo,
      synapseDelimiter.toString,
      true,
      {
        val synapseService = synapseServiceFactory(synapseUsername, synapsePassword)
        val csvFuture = synapseService.getTableAsCsv(importInfo.tableId)
        val lines = result(csvFuture, timeout).split(synapseEol)
        (lines.iterator, lines.length)
      }
    ).map(_ => ())

  private def createCsvFileLineIteratorAndCount(
    pathOrFile: Either[String, java.io.File],
    charsetName: Option[String],
    eol: Option[String]
  ): (Iterator[String], Int) = {
    def createSource = {
      val charset = Charset.forName(charsetName.getOrElse(defaultCharset))

      if (pathOrFile.isLeft)
        Source.fromFile(pathOrFile.left.get)(charset)
      else
        Source.fromFile(pathOrFile.right.get)(charset)
    }

    try {
      eol match {
        case Some(eol) => {
          // TODO: not effective... if a custom eol is used we need to read the whole file into memory and split again. It'd be better to use a custom BufferedReader
          val lines = createSource.mkString.split(eol)
          (lines.iterator, lines.length)
        }
        case None => {
          // TODO: not effective... To count the lines, need to recreate the source and parse the file again
          (createSource.getLines, createSource.getLines.size)
        }
      }
    } catch {
      case e: MalformedInputException => throw AdaParseException("Malformed input detected. It's most likely due to some special characters. Try a different chartset.", e)
    }
  }

  /**
    * Parses and imports data set using a provided line iterator
 *
    * @param importInfo
    * @param delimiter
    * @param skipFirstLine
    * @param createLineIteratorAndCount
    * @return The column names (future)
    */
  protected def importLineParsableDataSet(
    importInfo: DataSetImportInfo,
    delimiter: String,
    skipFirstLine: Boolean,
    createLineIteratorAndCount: => (Iterator[String], Int)
  ): Future[Seq[String]] = {
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    val dataSetAccessor =
      result(dsaf.register(
        importInfo.dataSpaceName,
        importInfo.dataSetId,
        importInfo.dataSetName,
        importInfo.setting
      ), timeout)

    val dataRepo = dataSetAccessor.dataSetRepo
    val syncDataRepo = RepoSynchronizer(dataRepo, timeout)

    // remove the records from the collection
    syncDataRepo.deleteAll

    val columnNamesFuture = {
      try {
        val (lines, lineCount) = createLineIteratorAndCount

        // collect the column names
        val columnNames =  getColumnNames(delimiter, lines)
        val columnCount = columnNames.size

        // for each line create a JSON record and insert to the database
        val contentLines = if (skipFirstLine) lines.drop(1) else lines
        val reportLineSize = (lineCount - 1) * reportLineFreq

        var bufferedLine = ""

        // read all the lines
        val lineFutures = contentLines.zipWithIndex.map { case (line, index) =>
          // parse the line
          bufferedLine += line
          val values = parseLine(delimiter, bufferedLine)

          if (values.size < columnCount) {
            logger.info(s"Buffered line ${index} has an unexpected count '${values.size}' vs '${columnCount}'. Buffering...")
            Future(())
          } else if (values.size > columnCount) {
            throw new AdaParseException(s"Buffered line ${index} has overflown an unexpected count '${values.size}' vs '${columnCount}'. Parsing terminated.")
          } else {
            // reset buffer
            bufferedLine = ""

            // create a JSON record
            val jsonRecord = JsObject(
              (columnNames, values).zipped.map {
                case (columnName, value) => (columnName, if (value.isEmpty) JsNull else JsString(value))
              })

            // insert the record to the database
            dataRepo.save(jsonRecord).map(_ =>
              logProgress((index + 1), reportLineSize, lineCount - 1)
            )
          }
        }

        Future.sequence(lineFutures).map(_ => columnNames)
      } catch {
        case e: Exception => Future.failed(e)
      }
    }

    columnNamesFuture.map { columnNames =>
      logger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
      columnNames
    }
  }

  override def inferDictionary(
    dataSetId: String,
    typeInferenceProvider: TypeInferenceProvider,
    fieldGroupSize: Int
  ): Future[Unit] = {
    logger.info(s"Dictionary inference for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo
    val fieldSyncRepo = RepoSynchronizer(fieldRepo, timeout)

    // init dictionary if needed
    result(fieldRepo.initIfNeeded, timeout)
    fieldSyncRepo.deleteAll

    val fieldNames = result(getFieldNames(dataRepo), timeout)
    val groupedFieldNames = fieldNames.filter(_ != "_id").grouped(fieldGroupSize).toSeq
    val futures = groupedFieldNames.par.map { fieldNames =>
      // Don't care about the result here, that's why we ignore ids and return Unit
      for {
        fieldNameAndTypes <- inferFieldTypes(dataRepo, typeInferenceProvider, fieldNames)
        ids = for ((fieldName, fieldType) <- fieldNameAndTypes) yield
          fieldRepo.save(Field(fieldName, fieldType, false))
        _ <- Future.sequence(ids)
      } yield
        ()
    }

    Future.sequence(futures.toList).map( _ =>
      logger.info(s"Dictionary inference for data set '${dataSetId}' successfully finished.")
    )
  }

  protected def importAndInferTranSMARTDictionary(
    dataSetId: String,
    typeInferenceProvider: TypeInferenceProvider,
    fieldGroupSize: Int,
    delimiter: String,
    createMappingFileLineIteratorAndCount: => (Iterator[String], Int),
    fieldNamesInOrder: Seq[String]
  ): Future[Unit] = {
    logger.info(s"Dictionary inference and TranSMART import for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo
    val fieldSyncRepo = RepoSynchronizer(fieldRepo, timeout)
    val categoryRepo = dsa.categoryRepo
    val categorySyncRepo = RepoSynchronizer(categoryRepo, timeout)

    // init field dictionary if needed
    result(fieldRepo.initIfNeeded, timeout)
    fieldSyncRepo.deleteAll

    // init categories if needed
    result(categoryRepo.initIfNeeded, timeout)
    categorySyncRepo.deleteAll

    val indexFieldNameMap = fieldNamesInOrder.zipWithIndex.map(_.swap).toMap
    val nameCategoryMap = MMap[String, Category]()

    val (lines, lineCount) = createMappingFileLineIteratorAndCount

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
          fieldNameAndTypes <- inferFieldTypes(dataRepo, typeInferenceProvider, fieldNames)
          ids = for ((fieldName, fieldType) <- fieldNameAndTypes) yield {
            val (fieldLabel, categoryName) = fieldNameLabelCategoryNameMap.getOrElse(fieldName, ("", None))
            val categoryId = categoryName.map(categoryNameIdMap.get(_).get)
            fieldRepo.save(Field(fieldName, fieldType, false, None, Seq[String](), Some(fieldLabel), categoryId))
          }
          _ <- Future.sequence(ids)
        } yield
          ()
      }
      Future.sequence(futures.toList)
    }

    finalFuture.map( _ =>
      logger.info(s"Dictionary inference and TranSMART import for data set '${dataSetId}' successfully finished.")
    )
  }

  def inferFieldTypes(
    dataRepo: JsObjectCrudRepo,
    typeInferenceProvider: TypeInferenceProvider,
    fieldNames: Traversable[String]
  ): Future[Traversable[(String, FieldType.Value)]] = {
    val projection =
      JsObject(fieldNames.map( fieldName => (fieldName, Json.toJson(1))).toSeq)

    // get all the values for a given field and infer
    dataRepo.find(None, None, Some(projection)).map { jsons =>
      fieldNames.map { fieldName =>
        val fieldType = inferFieldType(typeInferenceProvider, fieldName)(jsons)
        (fieldName, fieldType)
      }
    }
  }

  override def inferFieldType(
    dataRepo: JsObjectCrudRepo,
    typeInferenceProvider: TypeInferenceProvider,
    fieldName : String
  ): Future[FieldType.Value] =
    // get all the values for a given field and infer
    dataRepo.find(None, None, Some(Json.obj(fieldName -> 1))).map(
      inferFieldType(typeInferenceProvider, fieldName)
    )

  private def inferFieldType(
    typeInferenceProvider: TypeInferenceProvider,
    fieldName: String)(
    jsons: Traversable[JsObject]
  ): FieldType.Value = {
    val values = jsons.map { item =>
      val jsValue = (item \ fieldName).get
      jsValue match {
        case JsNull => None
        case x: JsString => Some(jsValue.as[String])
        case _ => Some(jsValue.toString)
      }
    }.flatten.toSet

    typeInferenceProvider.getType(values)
  }

  protected def getFieldNames(dataRepo: JsObjectCrudRepo): Future[Set[String]] =
    for {
      records <- dataRepo.find(None, None, None, Some(1))
    } yield
      records.headOption.map(_.keys).getOrElse(
        throw new AdaException(s"No records found. Unable to obtain field names. The associated data set might be empty.")
      )

  protected def getColumnNames(delimiter: String, lineIterator: Iterator[String]) =
    lineIterator.take(1).map {
      _.split(delimiter).map(columnName =>
        escapeKey(columnName.replaceAll("\"", "").trim)
      )}.flatten.toList

  // parse the lines, returns the parsed items
  private def parseLine(delimiter: String, line: String): Seq[String] = {
    val itemsWithStartEndFlag = line.split(delimiter, -1).map { l =>
      val start = if (l.startsWith("\"")) 1 else 0
      val end = if (l.endsWith("\"")) l.size - 1 else l.size
      val item = if (start <= end)
        l.substring(start, end).trim.replaceAll("\\\\\"", "\"")
      else
        ""
      (item, l.startsWith("\""), l.endsWith("\""))
    }

    fixImproperQuotes(delimiter, itemsWithStartEndFlag)
  }

  // fix the items that have been improperly split because the delimiter was located inside the quotes
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