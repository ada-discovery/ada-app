package standalone

import play.api.libs.json.{JsNull, JsString, JsObject}
import scala.concurrent.duration._

import scala.concurrent.Await
import scala.io.Source

class MergeBirthdayDeNoPaBaseline extends Runnable {

  val filenameWithBirthday = "/Users/peter.banda/Documents/DeNoPa/Denopa-V1-BL-Datensatz-1-dates.csv"
  val originalFilename = "/Users/peter.banda/Documents/DeNoPa/Denopa-V1-BL-Datensatz-1.csv"
  val mergedFilename = "/Users/peter.banda/Documents/DeNoPa/Denopa-V1-BL-Datensatz-1.csv"

//  val filename = "/home/peter.banda/DeNoPa/Denopa-V1-BL-Datensatz-1.csv"
//  val filename = "/home/tremor/Downloads/DeNoPa/Denopa-V1-BL-Datensatz-1_w_§§.csv"
//  val filename = "/home/tremor/Downloads/DeNoPa/Denopa-V1-BL-Datensatz-1_w_§§.csv"

  val separator = "§§"
  val birthDaySeparator = ","

  val idColumnIndex = 1
  val birthdayColumnIndex = 2

  val originalIdColumnIndex = 1
  val originalBirthdayColumnIndex = 3

  override def run = {
    // read all the lines
    val birthdayLines = Source.fromFile(filenameWithBirthday).getLines

    val idBirthdayMap = birthdayLines.drop(1).map { line =>
      val elements = line.split(birthDaySeparator).toSeq
      (elements(idColumnIndex), elements(birthdayColumnIndex))
    }.toMap

    // read all the lines
    val lines = Source.fromFile(originalFilename).getLines

    lines.drop(1).zipWithIndex.foreach { case (line, index) =>

      // parse the line
      val values = line.split(separator).toSeq

      // check if the number of items is as expected
      if (values.size != 5647) {
        println(values.mkString("\n"))
        throw new IllegalStateException(s"Line ${index} has a bad count '${values.size}'!!!")
      }

      val id = values(originalIdColumnIndex)
      val newBirthday = idBirthdayMap.get(id).get

      val newValues = values.take(originalBirthdayColumnIndex - 1) ++ List(newBirthday) ++ values.drop(originalBirthdayColumnIndex)

      System.out.println(newValues.size)

      println(s"Record $index processed.")
    }
  }
}

object MergeBirthdayDeNoPaBaseline extends GuiceBuilderRunnable[MergeBirthdayDeNoPaBaseline] with App {
  override def main(args: Array[String]) = run
}
