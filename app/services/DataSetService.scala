package services

import javax.inject.Inject

import com.google.inject.ImplementedBy
import persistence.RepoSynchronizer
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.{JsString, JsNull, JsObject}
import runnables.DataSetImportInfo
import scala.concurrent.Await.result
import scala.concurrent.duration._
import util.JsonUtil._

import scala.concurrent.Await._
import scala.io.Source

@ImplementedBy(classOf[DataSetServiceImpl])
trait DataSetService {
  def importDataSet(importInfo: DataSetImportInfo)
}

class DataSetServiceImpl @Inject()(
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dsaf: DataSetAccessorFactory
  ) extends DataSetService{

  private val timeout = 120000 millis

  override def importDataSet(importInfo: DataSetImportInfo): Unit = {
    val dataSetAccessor =
      result(dsaf.register(importInfo.dataSpaceName, importInfo.dataSetId, importInfo.dataSetName, importInfo.setting), timeout)
    val dataRepo = dataSetAccessor.dataSetRepo
    val syncDataRepo = RepoSynchronizer(dataRepo, timeout)

    // remove the records from the collection
    syncDataRepo.deleteAll

    // read all the lines
    val lines = getLineIterator(importInfo)

    // collect the column names
    val columnNames =  getColumnNames(importInfo, lines)
    val columnCount = columnNames.size
    var bufferedLine = ""

    // for each line create a JSON record and insert to the database
    val contentLines = if (importInfo.eol.isDefined) lines.drop(1) else lines
    contentLines.zipWithIndex.foreach { case (line, index) =>
      // parse the line
      bufferedLine += line
      val values = parseLine(importInfo, bufferedLine)

      if (values.size < columnCount) {
        println(s"Buffered line ${index} has an unexpected count '${values.size}' vs '${columnCount}'. Buffering...")
      } else if (values.size > columnCount) {
        val message = s"Buffered line ${index} has overflown an unexpected count '${values.size}' vs '${columnCount}'. Terminating..."
        println(message)
        println(values.mkString(","))
        throw new IllegalArgumentException(message)
      } else {
        // create a JSON record
        val jsonRecord = JsObject(
          (columnNames, values).zipped.map {
            case (columnName, value) => (columnName, if (value.isEmpty) JsNull else JsString(value))
          })

        // insert the record to the database
        syncDataRepo.save(jsonRecord)
        println(s"Record $index imported.")

        // reset buffer
        bufferedLine = ""
      }
    }
  }

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

  protected def getColumnNames(importInfo: DataSetImportInfo, lineIterator: Iterator[String]) =
    lineIterator.take(1).map {
      _.split(importInfo.delimiter).map(columnName =>
        escapeKey(columnName.replaceAll("\"", "").trim)
      )}.flatten.toList

  // parse the lines, returns the parsed items
  private def parseLine(importInfo: DataSetImportInfo, line: String) =
    line.split(importInfo.delimiter).map { l =>
      val start = if (l.startsWith("\"")) 1 else 0
      val end = if (l.endsWith("\"")) l.size - 1 else l.size
      l.substring(start, end).trim.replaceAll("\\\\\"", "\"")
    }
}