package services

import java.nio.charset.{MalformedInputException, Charset}
import javax.inject.Inject

import com.google.inject.ImplementedBy
import com.twitter.chill.TraversableSerializer
import models.{AdaException, AdaParseException, FieldType, Field}
import persistence.RepoSynchronizer
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{Json, JsString, JsNull, JsObject}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import runnables.DataSetImportInfo
import util.TypeInferenceProvider
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.Await.result
import scala.concurrent.duration._
import util.JsonUtil._

import scala.io.Source
import scala.collection.Set

@ImplementedBy(classOf[DataSetServiceImpl])
trait DataSetService {

  def importDataSet(
    importInfo: DataSetImportInfo
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
    dsaf: DataSetAccessorFactory
  ) extends DataSetService{

  private val logger = Logger
  private val timeout = 120000 millis
  private val defaultCharset = "UTF-8"
  private val reportLineFreq = 0.1

  override def importDataSet(importInfo: DataSetImportInfo) = {
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    val dataSetAccessor =
      result(dsaf.register(importInfo.dataSpaceName, importInfo.dataSetId, importInfo.dataSetName, importInfo.setting), timeout)
    val dataRepo = dataSetAccessor.dataSetRepo
    val syncDataRepo = RepoSynchronizer(dataRepo, timeout)

    // remove the records from the collection
    syncDataRepo.deleteAll

    val future = {
      try {
        val (lines, lineCount) = getLineIteratorAndCount(importInfo)

        // collect the column names
        val columnNames =  getColumnNames(importInfo.delimiter, lines)
        val columnCount = columnNames.size

        // for each line create a JSON record and insert to the database
        val contentLines = if (importInfo.eol.isDefined) lines.drop(1) else lines
        val reportLineSize = (lineCount - 1) * reportLineFreq

        var bufferedLine = ""

        // read all the lines
        val lineFutures = contentLines.zipWithIndex.map { case (line, index) =>
          // parse the line
          bufferedLine += line
          val values = parseLine(importInfo.delimiter, bufferedLine)

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

        Future.sequence(lineFutures)
      } catch {
        case e: Exception => Future.failed(e)
      }
    }

    future.map( _ =>
      logger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
    )
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

  protected def getLineIteratorAndCount(importInfo: DataSetImportInfo): (Iterator[String], Int) =
    try {
      importInfo.eol match {
        case Some(eol) => {
          // TODO: not effective... if a custom eol is used we need to read the whole file into memory and split again. It'd be better to use a custom BufferedReader
          val lines = createSource(importInfo).mkString.split(eol)
          (lines.iterator, lines.length)
        }
        case None => {
          // TODO: not effective... To count the lines, need to recreate the source and parse the file again
          (createSource(importInfo).getLines, createSource(importInfo).getLines.size)
        }
      }
    } catch {
      case e: MalformedInputException => throw AdaParseException("Malformed input detected. It's most likely due to some special characters. Try a different chartset.", e)
    }

  private def createSource(importInfo: DataSetImportInfo) = {
    val charsetName = importInfo.charsetName.getOrElse(defaultCharset)
    val charset = Charset.forName(charsetName)

    if (importInfo.path.isDefined)
      Source.fromFile(importInfo.path.get)(charset)
    else
      Source.fromFile(importInfo.file.get)(charset)
  }

  protected def getColumnNames(delimiter: String, lineIterator: Iterator[String]) =
    lineIterator.take(1).map {
      _.split(delimiter).map(columnName =>
        escapeKey(columnName.replaceAll("\"", "").trim)
      )}.flatten.toList

  // parse the lines, returns the parsed items
  private def parseLine(delimiter: String, line: String): Seq[String] = {
    val itemsWithStartEndFlag = line.split(delimiter).map { l =>
      val start = if (l.startsWith("\"")) 1 else 0
      val end = if (l.endsWith("\"")) l.size - 1 else l.size
      val item = if (start <= end)
        l.substring(start, end).trim.replaceAll("\\\\\"", "\"")
      else
        ""
      (item, l.startsWith("\""), l.endsWith("\""))
    }

    fixImproperQuotes(itemsWithStartEndFlag)
  }

  // fix the items that have been improperly split because the delimiter was located inside the quotes
  private def fixImproperQuotes(itemsWithStartEndFlag: Array[(String, Boolean, Boolean)]) = {
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
          bufferedItem += item
        } else
          throw new AdaParseException(s"Parsing failed. The item '$item' ends with a quote but there is no preceding quote to match with.")

      } else {
        if (!startFlag && !endFlag) {
          // continue buffering
          bufferedItem += item
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