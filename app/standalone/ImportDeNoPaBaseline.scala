package standalone

import javax.inject.Inject

import persistence.DeNoPaBaselineRepo
import play.api.libs.json.{JsNull, JsString, JsObject}
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._

import scala.concurrent.Await
import scala.io.Source
import util.encodeMongoKey

class ImportDeNoPaBaseline @Inject()(baselineRepo: DeNoPaBaselineRepo) extends Runnable {

  val folder = "/home/peter.banda/DeNoPa/"

  val filename = folder + "Denopa-V1-BL-Datensatz-1-final.csv"
  val separator = "§§"
  val timeout = 50000 millis

  override def run = {
    // remove the records from the collection
    val deleteFuture = baselineRepo.deleteAll
    Await.result(deleteFuture, timeout)

    // read all the lines
    val lines = Source.fromFile(filename).getLines

    // collect the column names
    val columnNames =  "Line_Nr" :: lines.take(1).map {
      _.split(separator).map(columnName =>
        encodeMongoKey(columnName.replaceAll("\"", "").trim)
    )}.flatten.toList

    // for each lince create a JSON record and insert to the database
    lines.zipWithIndex.foreach { case (line, index) =>

      // parse the line
      val values = parseLine(line)

//      val appValues = values.filter(_.contains("\""))
//      if (!appValues.isEmpty)
//        println(appValues.mkString("\n"))

      // check if the number of items is as expected
      if (values.size != 5647) {
        println(values.mkString("\n"))
        throw new IllegalStateException(s"Line ${index} has a bad count '${values.size}'!!!")
      }

      // create a JSON record
      val jsonRecord = JsObject(
        (columnNames, values).zipped.map {
          case (columnName, value) => (columnName, if (value.isEmpty) JsNull else JsString(value))
        })

      // insert the record to the database
      val future = baselineRepo.save(jsonRecord)

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

object ImportDeNoPaBaseline extends GuiceBuilderRunnable[ImportDeNoPaBaseline] with App {
  override def main(args: Array[String]) = run
}
