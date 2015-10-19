package standalone

import javax.inject.Inject

import persistence.{DeNoPaFirstVisitRepo, DeNoPaBaselineRepo}
import play.api.libs.json.{JsNull, JsString, JsObject}
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._

import scala.concurrent.Await
import scala.io.Source
import util.encodeMongoKey

class ImportDeNoPaFirstVisit @Inject()(firstVisitRepo: DeNoPaFirstVisitRepo) extends Runnable {

  val filename = "/Users/peter.banda/Documents/DeNoPa/Denopa-V2-FU1-Datensatz.sav-unfiltered.csv"
  val timeout = 50000 millis

  override def run = {
    // remove the records from the collection
    val deleteFuture = firstVisitRepo.deleteAll
    Await.result(deleteFuture, timeout)

    // read all the lines
    val lines = Source.fromFile(filename).getLines

    // collect the column names
    val columnNames = lines.take(1).map {
      _.split(",").map(columnName =>
        encodeMongoKey(columnName.replaceAll("\"", "").trim)
    )}.toSeq.flatten

    val badLineIndeces = List(98, 122, 123, 154, 155, 219, 220)

    // for each lince create a JSON record and insert to the database
    lines.zipWithIndex.foreach { case (line, index) =>
      // dirty fix of the "", problem
      val fixedLine = if (index == 56)
        line.replaceAll("\"\"habe mit dem Sprechen Probleme\"\",", "\"\"habe mit dem Sprechen Probleme\"\" ,").replaceAll("\"\"der Mund würde schwer\"\",", "\"\"der Mund würde schwer\"\" ,")
      else if (index == 101)
        line.replaceAll("\"\"zu verzetteln\"\",", "\"\"zu verzetteln\"\" ,")
      else if (index == 159)
        line.replaceAll("\"\"verliert den Faden\"\",","\"\"verliert den Faden\"\" ,").replaceAll("\"\"Burn-Out\"\",", "\"\"Burn-Out\"\" ,")
      else if (index == 163)
        line.replaceAll("\"\"Gefühl\"\",", "\"\"Gefühl\"\" ,")
      else if (index == 211 || index == 212)
        line.replaceAll("\"\"Migräne\"\",", "\"\"Migräne\"\" ,")
      else
        line

      // println(values.mkString("\n"))

      // parse the line
      val values = parseLine(fixedLine)

      if (!badLineIndeces.contains(index)) {
        // check if the number of items is as expected
        if (values.size != 8918) {
          println(values.filter(_.contains("\"")).mkString("\n"))
          throw new IllegalStateException(s"Line ${index} has a bad count '${values.size}'!!!")
        }

        // create a JSON record
        val jsonRecord = JsObject(
          (columnNames, values).zipped.map {
            case (columnName, value) => (
              if (columnName.isEmpty) "Line_Nr" else columnName,
              if (value.isEmpty) JsNull else JsString(value)
              )
          })

        // insert the record to the database
        val future = firstVisitRepo.save(jsonRecord)

        // wait for the execution to complete, i.e., synchronize
        Await.result(future, timeout)

        println(s"Record $index imported.")
      }
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

object ImportDeNoPaFirstVisit extends GuiceBuilderRunnable[ImportDeNoPaFirstVisit] with App {
  override def main(args: Array[String]) = run
}
