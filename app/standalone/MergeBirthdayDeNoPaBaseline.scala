package standalone

import play.api.libs.json.{JsNull, JsString, JsObject}
import scala.concurrent.duration._

import scala.concurrent.Await
import scala.io.Source
import play.api.Play.current

class MergeBirthdayDeNoPaBaseline extends Runnable {

//  val folder = "/Users/peter.banda/Documents/DeNoPa/"
  val folder = "/home/tremor/Downloads/DeNoPa/"

  val filenameWithBirthday = folder + "Denopa-V1-BL-Datensatz-1-dates.csv"
  val originalFilename = folder + "Denopa-V1-BL-Datensatz-1.csv"
  val mergedFilename = folder + "Denopa-V1-BL-Datensatz-1-final.csv"

  val separator = "§§"
  val birthDaySeparator = ","

  val idColumnIndex = 1
  val birthdayColumnIndex = 2

  val originalIdColumnIndex = 1
  val originalBirthdayColumnIndex = 4

  override def run = {
    // read all the lines
    val birthdayLines = Source.fromFile(filenameWithBirthday).getLines

    val idBirthdayMap = birthdayLines.drop(1).map { line =>
      val elements = line.split(birthDaySeparator).toSeq
      (elements(idColumnIndex), elements(birthdayColumnIndex))
    }.toMap

    // read all the lines
    val lines = Source.fromFile(originalFilename).getLines

    val sb = new StringBuilder(10000)
    val header = lines.take(1).toSeq(0)

    sb.append(header + "\n")

    val newLines = lines.zipWithIndex.map { case (line, index) =>

      // parse the line
      val values = line.split(separator).toSeq

      // check if the number of items is as expected
      if (values.size != 5647) {
        println(values.mkString("\n"))
        throw new IllegalStateException(s"Line ${index} has a bad count '${values.size}'!!!")
      }

      val id = values(originalIdColumnIndex)
      val newBirthday = idBirthdayMap.get(id).get
      val newBirthdayString = if (!newBirthday.toLowerCase.equals("na"))
        "\"" + newBirthday + "\""
      else
        newBirthday

      val newValues = values.take(originalBirthdayColumnIndex) ++ List(newBirthdayString) ++ values.drop(originalBirthdayColumnIndex + 1)
      if (newValues.size != 5647) {
        println(values.mkString("\n"))
        throw new IllegalStateException(s"Line ${index} has a bad count '${values.size}'!!!")
      }

      val newLine = newValues.mkString(separator)
      System.out.println(line.take(800))
      System.out.println(newLine.take(800))

      println(s"Record $index processed.")

      newLine
    }

    sb.append(newLines.mkString("\n"))
    scala.tools.nsc.io.File(mergedFilename).writeAll(sb.toString)
  }
}

object MergeBirthdayDeNoPaBaseline extends GuiceBuilderRunnable[MergeBirthdayDeNoPaBaseline] with App {
  override def main(args: Array[String]) = run
}
