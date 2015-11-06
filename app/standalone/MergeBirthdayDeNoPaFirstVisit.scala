package standalone

import play.api.libs.json.{JsNull, JsString, JsObject}
import scala.concurrent.duration._

import scala.concurrent.Await
import scala.io.Source
import play.api.Play.current

class MergeBirthdayDeNoPaFirstVisit extends Runnable {

//  val folder = "/Users/peter.banda/Documents/DeNoPa/"
  val folder = "/home/tremor/Downloads/DeNoPa/"

  val filenameWithBirthday = folder + "Denopa-V2-FU1-Datensatz-dates.csv"
  val originalFilename = folder + "Denopa-V2-FU1-Datensatz.csv"
  val mergedFilename = folder + "Denopa-V2-FU1-Datensatz-final.csv"

  val separator = "§§"
  val birthDaySeparator = ","

  val idColumnIndex = 1
  val birthdayColumnIndex = 2

  val originalIdColumnIndex = 1
  val originalBirthdayColumnIndex = 2

  val splitLineIndeces = List(122, 154, 219)

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

      if (!splitLineIndeces.contains(index - 1)) {
        // parse the line
        val values = line.split(separator).toSeq

        val id = values(originalIdColumnIndex)
        val newBirthday = idBirthdayMap.get(id).get
        val newBirthdayString = if (!newBirthday.toLowerCase.equals("na"))
          "\"" + newBirthday + "\""
        else
          newBirthday

        val newValues = values.take(originalBirthdayColumnIndex) ++ List(newBirthdayString) ++ values.drop(originalBirthdayColumnIndex + 1)

        if (values.size != newValues.size) {
          println(values.mkString("\n"))
          throw new IllegalStateException(s"Line ${index} has a bad count '${values.size}'!!!")
        }

        val newLine = newValues.mkString(separator)
        System.out.println(line.take(800))
        System.out.println(newLine.take(800))

        println(s"Record $index processed.")

        newLine
      } else {
        println(s"Record $index processed - copied.")
        line
      }
    }

    sb.append(newLines.mkString("\n"))
    scala.tools.nsc.io.File(mergedFilename).writeAll(sb.toString)
  }
}

object MergeBirthdayDeNoPaFirstVisit extends GuiceBuilderRunnable[MergeBirthdayDeNoPaFirstVisit] with App {
  override def main(args: Array[String]) = run
}
