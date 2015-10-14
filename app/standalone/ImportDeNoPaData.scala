package standalone

import javax.inject.Inject

import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.collection.JSONCollection

import scala.io.Source

class ImportDeNoPaData @Inject() (reactiveMongoApi: ReactiveMongoApi) extends Runnable {

  val filename = "/Users/peter.banda/Documents/DeNoPa/Denopa-V1-BL-Datensatz-1.unfiltered.csv"
  val collection: JSONCollection = reactiveMongoApi.db.collection("denopa")

  private def parseNaive = {
    val lines = Source.fromFile(filename).getLines

    lines.zipWithIndex.foreach{ case (line, index) =>
      val values = line.split(",")
      println(s"$index : ${values.size}")
    }
  }

  private def parseBetter = {
    val lines = Source.fromFile(filename).getLines

    lines.zipWithIndex.foreach{ case (line, index) =>
      val values = line.split("\",").map(l =>
        if (l.startsWith("\""))
          Array(l.substring(1))
        else if (l.contains("\"")) {
          val l2 = l.split("\"", 2)
          l2(0).split(',') ++ List(l2(1))
        } else
          l.replaceAll("\"","").split(',')
      ).flatten

      print(s"$index : ${values.size}")
      if (values.size != 5647) println(" bad count !!!") else println
    }
  }

  private def parseFinal = {
    val lines = Source.fromFile(filename).getLines

    lines.zipWithIndex.foreach{ case (line, index) =>

      // dirty fix of the "", problem
      val linex = if (index == 162)
        line.replaceFirst("\"\"huschen\"\",", "\"\"huschen\"\" ,")
      else
        line

      val values = linex.split("\",").map(l =>
        if (l.startsWith("\""))
          Array(l.substring(1))
        else if (l.contains("\"")) {
          val l2 = l.split("\"", 2)
          l2(0).split(',') ++ List(l2(1))
        } else
          l.replaceAll("\"","").split(',')
      ).flatten

      print(s"$index : ${values.size}")
      if (values.size != 5647) println(" bad count !!!") else println
    }
  }

  override def run = parseNaive
}

object ImportDeNoPaData extends GuiceBuilderRunnable[ImportDeNoPaData] with App {
  override def main(args:Array[String]) = run
}
