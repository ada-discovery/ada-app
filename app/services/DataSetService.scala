package services

import java.nio.charset.{MalformedInputException, Charset}
import javax.inject.Inject

import com.google.inject.ImplementedBy
import models.{AdaException, AdaParseException, FieldType, Field}
import persistence.RepoSynchronizer
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
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

  def importDataSet(importInfo: DataSetImportInfo): Unit

  def inferDictionary(
    dataSetId: String,
    typeInferenceProvider: TypeInferenceProvider
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

  private val timeout = 120000 millis
  private val defaultCharset = "UTF-8"

  override def importDataSet(importInfo: DataSetImportInfo) = {
    val dataSetAccessor =
      result(dsaf.register(importInfo.dataSpaceName, importInfo.dataSetId, importInfo.dataSetName, importInfo.setting), timeout)
    val dataRepo = dataSetAccessor.dataSetRepo
    val syncDataRepo = RepoSynchronizer(dataRepo, timeout)

    // remove the records from the collection
    syncDataRepo.deleteAll

    // read all the lines
    val lines = getLineIterator(importInfo)

    // collect the column names
    val columnNames =  getColumnNames(importInfo.delimiter, lines)
    val columnCount = columnNames.size
    var bufferedLine = ""

    // for each line create a JSON record and insert to the database
    val contentLines = if (importInfo.eol.isDefined) lines.drop(1) else lines

    try {
      contentLines.zipWithIndex.foreach { case (line, index) =>
        // parse the line
        bufferedLine += line
        val values = parseLine(importInfo.delimiter, bufferedLine)

        if (values.size < columnCount) {
          println(s"Buffered line ${index} has an unexpected count '${values.size}' vs '${columnCount}'. Buffering...")
        } else if (values.size > columnCount) {
          val message = s"Buffered line ${index} has overflown an unexpected count '${values.size}' vs '${columnCount}'. Parsing terminated."
          println(message)
          throw new AdaParseException(message)
        } else {
          // create a JSON record
          val jsonRecord = JsObject(
            (columnNames, values).zipped.map {
              case (columnName, value) => (columnName, if (value.isEmpty) JsNull else JsString(value))
            })

          // TODO: Could we stay async here? Do we care about the order of insertion?
          // insert the record to the database
          syncDataRepo.save(jsonRecord)
          println(s"Record $index imported.")

          // reset buffer
          bufferedLine = ""
        }
      }
    } catch {
      case e: MalformedInputException => throw AdaParseException("Malformed input detected. It's most likely due to some special characters. Try a different chartset.", e)
    }
  }

  override def inferDictionary(
    dataSetId: String,
    typeInferenceProvider: TypeInferenceProvider
  ): Future[Unit] = {
    val dsa = dsaf(dataSetId).get
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo
    val fieldSyncRepo = RepoSynchronizer(fieldRepo, timeout)
    // init dictionary if needed
    result(fieldRepo.initIfNeeded, timeout)
    fieldSyncRepo.deleteAll

    val fieldNames = result(getFieldNames(dataRepo), timeout)
    val futures = fieldNames.filter(_ != "_id").par.map { fieldName =>
      println(fieldName)
      val fieldType = result(inferFieldType(dataRepo, typeInferenceProvider, fieldName), timeout)

      fieldRepo.save(Field(fieldName, fieldType, false))
    }

    Future.sequence(futures.toList).map( _ => ())
  }

  override def inferFieldType(
    dataRepo: JsObjectCrudRepo,
    typeInferenceProvider: TypeInferenceProvider,
    fieldName : String
  ): Future[FieldType.Value] =
    // get all the values for a given field and infer
    dataRepo.find(None, None, Some(Json.obj(fieldName -> 1))).map { items =>
      val values = items.map { item =>
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

  protected def getLineIterator(importInfo: DataSetImportInfo) = {
    val charsetName = importInfo.charsetName.getOrElse(defaultCharset)
    val charset = Charset.forName(charsetName)
    val source =
      if (importInfo.path.isDefined)
        Source.fromFile(importInfo.path.get)(charset)
      else
        Source.fromFile(importInfo.file.get)(charset)

    if (importInfo.eol.isDefined)
      source.mkString.split(importInfo.eol.get).iterator
    else
      source.getLines
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
}