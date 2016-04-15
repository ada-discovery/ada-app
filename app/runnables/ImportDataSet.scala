package runnables

import javax.inject.Inject

import models.DataSetSetting
import persistence.RepoSynchronizer
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.{Json, JsNull, JsObject, JsString}
import util.JsonUtil.escapeKey

import scala.concurrent.Await.result
import scala.concurrent.duration._
import scala.io.Source

class ImportDataSet(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    setting: Option[DataSetSetting],
    folder: String,
    filename: String,
    separator: String,
    eol: Option[String] = None
  ) extends Runnable {

  @Inject protected var dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo = _
  @Inject protected var dsaf: DataSetAccessorFactory = _

  protected val timeout = 120000 millis

  lazy val dataSetAccessor =
    result(dsaf.register(dataSpaceName, dataSetId, dataSetName, setting), timeout)

  override def run = {
    val dataRepo = dataSetAccessor.dataSetRepo
    val syncDataRepo = RepoSynchronizer(dataRepo, timeout)

    // remove the records from the collection
    syncDataRepo.deleteAll

    // read all the lines
    val lines = getLineIterator

    // collect the column names
    val columnNames =  getColumnNames(lines)
    val columnCount = columnNames.size
    var bufferedLine = ""

    // for each line create a JSON record and insert to the database
    val contentLines = if (eol.isDefined) lines.drop(1) else lines
    contentLines.zipWithIndex.foreach { case (line, index) =>
      // parse the line
      bufferedLine += line
      val values = parseLine(bufferedLine)

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

        // insert the record to the database
        syncDataRepo.save(jsonRecord)
        println(s"Record $index imported.")

        // reset buffer
        bufferedLine = ""
      }
    }
  }

  protected def getLineIterator = {
    val source = Source.fromFile(folder + filename)
    if (eol.isDefined)
      source.mkString.split(eol.get).iterator
    else
      source.getLines
  }

  protected def getColumnNames(lineIterator: Iterator[String]) =
    lineIterator.take(1).map {
    _.split(separator).map(columnName =>
      escapeKey(columnName.replaceAll("\"", "").trim)
    )}.flatten.toList

  // parse the lines, returns the parsed items
  private def parseLine(line: String) =
    line.split(separator).map { l =>
      val start = if (l.startsWith("\"")) 1 else 0
      val end = if (l.endsWith("\"")) l.size - 1 else l.size
      l.substring(start, end).trim.replaceAll("\\\\\"", "\"")
    }
}