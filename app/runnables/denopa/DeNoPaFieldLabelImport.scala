package runnables.denopa

import scala.io.Source

object DeNoPaFieldLabelImport extends App {
  private val inputFile = "/home/peter/Data/DeNoPa/DeNoPa_all.csv"
  private val delimiter = ";"

  // Exam;group;label;sub group;original field name;associated question;type;values;note

  val lines = Source.fromFile(inputFile).getLines()
  val nameLabels = lines.flatMap { line =>
    val values = line.split(delimiter, -1)

    def value(index: Int) = {
      val string = values(index).trim
      if (string.isEmpty) None else Some(string)
    }

    val group = value(1)
    val label = value(2)
    val name = value(4)
    (name, label).zipped.headOption
  }

  nameLabels.foreach(println)
}
