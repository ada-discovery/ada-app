package services

import javax.inject.Inject

import com.google.inject.ImplementedBy
import models.{FieldType, Field}
import persistence.RepoSynchronizer
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.{Json, JsString, JsNull, JsObject}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import runnables.DataSetImportInfo
import util.TypeInferenceProvider
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
    contentLines.zipWithIndex.foreach { case (line, index) =>
      // parse the line
      bufferedLine += line
      val values = parseLine(importInfo.delimiter, bufferedLine)

      if (values.size < columnCount) {
        println(s"Buffered line ${index} has an unexpected count '${values.size}' vs '${columnCount}'. Buffering...")
      } else if (values.size > columnCount) {
        val message = s"Buffered line ${index} has overflown an unexpected count '${values.size}' vs '${columnCount}'. Terminating..."
        println(message)
        throw new IllegalArgumentException(message)
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
  }

  override def inferDictionary(
    dataSetId: String,
    typeInferenceProvider: TypeInferenceProvider
  ): Future[Unit] = {
    val dsa = dsaf(dataSetId).get
    val dataRepo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo
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
        throw new IllegalStateException(s"No records found. Unable to obtain field names. The associated data set might be empty.")
      )

  protected def getLineIterator(importInfo: DataSetImportInfo) = {
    val source =
      if (importInfo.path.isDefined)
        Source.fromFile(importInfo.path.get)
      else
        Source.fromFile(importInfo.file.get)

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
  private def parseLine(delimiter: String, line: String) =
    line.split(delimiter).map { l =>
      val start = if (l.startsWith("\"")) 1 else 0
      val end = if (l.endsWith("\"")) l.size - 1 else l.size
      l.substring(start, end).trim.replaceAll("\\\\\"", "\"")
    }
}