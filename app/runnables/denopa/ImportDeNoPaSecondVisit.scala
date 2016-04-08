package runnables.denopa

import javax.inject.Inject

import models.DataSetId._
import models.DataSetMetaInfo
import persistence.dataset.DataSetAccessorFactory
import play.api.Configuration
import play.api.libs.json.{JsNull, JsObject, JsString}
import runnables.GuiceBuilderRunnable
import util.JsonUtil.escapeKey

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

class ImportDeNoPaSecondVisit @Inject()(
    configuration: Configuration,
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  val folder = configuration.getString("denopa.import.folder").get

  val filename = folder + "DeNoPa.v3.sav_with_§§_and_§%w.csv"
  val separator = "§§"
  val eol = "§%w"
  val timeout = 50000 millis

  val metaInfo = DataSetMetaInfo(None, denopa_secondvisit, "DeNoPa Second Visit")

  val dataSetAccessor = {
    val futureAccessor = dsaf.register(metaInfo)
    Await.result(futureAccessor, 120000 millis)
  }

  override def run = {
    val dataRepo = dataSetAccessor.dataSetRepo
    // remove the records from the collection
    val deleteFuture = dataRepo.deleteAll
    Await.result(deleteFuture, timeout)

    // read all the lines
//    val lines = Source.fromFile(filename).getLines
    val lines = Source.fromFile(filename).mkString.split(eol)

    // collect the column names
    val columnNames =  "Line_Nr" :: lines.take(1).map {
      _.split(separator).map(columnName =>
        escapeKey(columnName.replaceAll("\"", "").trim)
      )}.flatten.toList

    // for each lince create a JSON record and insert to the database
    lines.drop(1).zipWithIndex.foreach { case (line, index) =>

      // parse the line
      val values = parseLine(line)

      if (values.size != 5191) {
        println(values.mkString("\n"))
        throw new IllegalStateException(s"Line ${index} has a bad count '${values.size}'!!!")
      }

      // create a JSON record
      val jsonRecord = JsObject(
        (columnNames, values).zipped.map {
          case (columnName, value) => (columnName, if (value.isEmpty) JsNull else JsString(value))
        })

      // insert the record to the database
      val future = dataRepo.save(jsonRecord)

      // wait for the execution to complete, i.e., synchronize
      Await.result(future, timeout)

      println(s"Record $index imported.")
    }
  }

  // parse the lines, returns the parsed items
  private def parseLine(line: String) =
    line.split(separator).map { l =>
      val start = if (l.startsWith("\"")) 1 else 0
      val end = if (l.endsWith("\"")) l.size - 1 else l.size
      l.substring(start, end).trim.replaceAll("\\\\\"", "\"")
    }
}

object ImportDeNoPaSecondVisit extends GuiceBuilderRunnable[ImportDeNoPaSecondVisit] with App {
  run
}