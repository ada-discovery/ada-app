package standalone

import scala.io.Source

object ImportDeNoPaDataWoDB extends App {

  class TypeEvidence(
    var intCount: Int,
    var longCount: Int,
    var doubleCount: Int,
    var booleanCount: Int,
    var nullCount: Int,
    var valueCountMap : Map[String, Int]
  )

  lazy val filename = "/Users/peter.banda/Documents/DeNoPa/Denopa-V1-BL-Datensatz-1.unfiltered.csv"

  private def parseNaive = {
    val lines = Source.fromFile(filename).getLines

    lines.zipWithIndex.foreach { case (line, index) =>
      val values = line.split(",")
      println(s"$index : ${values.size}")
    }
  }

  private def parseBetter = {
    val lines = Source.fromFile(filename).getLines

    lines.zipWithIndex.foreach { case (line, index) =>
      val values = line.split("\",").map(l =>
        if (l.startsWith("\""))
          Array(l.substring(1))
        else if (l.contains("\"")) {
          val l2 = l.split("\"", 2)
          l2(0).split(',') ++ List(l2(1))
        } else
          l.replaceAll("\"", "").split(',')
      ).flatten

      print(s"$index : ${values.size}")
      if (values.size != 5647) println(" bad count !!!") else println
    }
  }


  private def parseFinal = {
    val lines = Source.fromFile(filename).getLines

    val columnNames = lines.take(1).map {
      _.split(",").map(_.replaceAll("\"", "").trim())
    }.toSeq.flatten

    val matrix = lines.zipWithIndex.map { case (line, index) =>
      // dirty fix of the "", problem
      val linex = if (index == 161)
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
          l.replaceAll("\"", "").split(',')
      ).flatten

      if (values.size != 5647)
        throw new IllegalStateException(s"Line ${index} has a bad count '${values.size}'!!!")
      values
    }.toSeq

    columnNames.foreach(print(_))

    println()

    for (i <- 0 until matrix.size)
      println(matrix(i)(1))

    //    if (index == 0) println(s"Header : $line")
    //    print(s"$index : ${values.size}")
    //    if (values.size != 5647) println(" bad count !!!") else println
  }

  override def main(args:Array[String]) = parseNaive
}