package standalone

import javax.inject.Inject

import play.api.libs.json.{JsString, JsObject}
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.io.Source

class ImportDeNoPaData @Inject()(reactiveMongoApi: ReactiveMongoApi) extends Runnable {

  val filename = "/Users/peter.banda/Documents/DeNoPa/Denopa-V1-BL-Datensatz-1.unfiltered.csv"
  val denopaCollection: JSONCollection = reactiveMongoApi.db.collection("denopa")

  override def run = {
    val lines = Source.fromFile(filename).getLines

    // remove all records in the collection
    denopaCollection.drop()

    // collect the column names
    val columnNames = lines.take(1).map {
      _.split(",").map(_.replaceAll("\"", "").trim)
    }.toSeq.flatten

    // for each lince create a JSON record and insert to the database
    lines.zipWithIndex.foreach { case (line, index) =>
      // dirty fix of the "", problem
      val fixedLine = if (index == 161)
        line.replaceFirst("\"\"huschen\"\",", "\"\"huschen\"\" ,")
      else
        line

      // parse the line
      val values = parseLine(fixedLine)

      // check if the number of items is as expected
      if (values.size != 5647)
        throw new IllegalStateException(s"Line ${index} has a bad count '${values.size}'!!!")

      // create a JSON record
      val jsonRecord = JsObject(
        (columnNames, values).zipped.map {
          case (columnName, value) => (columnName, JsString(value))
        })

      // inser the record to the database
      denopaCollection.insert(jsonRecord)
      println(s"Record $index imported.")
    }
  }

  // parse the lines, returns the parsed items
  private def parseLine(line : String) =
    line.split("\",").map(l =>
      if (l.startsWith("\""))
        Array(l.substring(1))
      else if (l.contains("\"")) {
        val l2 = l.split("\"", 2)
        l2(0).split(',') ++ List(l2(1))
      } else
        l.replaceAll("\"", "").split(',')
    ).flatten.map(_.trim)
}

object ImportDeNoPaData extends GuiceBuilderRunnable[ImportDeNoPaData] with App {
  override def main(args: Array[String]) = run
}
