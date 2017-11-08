package runnables

import java.io._

import scala.io.Source

object MergePDGeneFiles extends App {
  private val folder = "/home/peter/Data/PD_Gene/"

  private val inputFileNames = Seq(
    "Neurox_clean.tsv",
    "jpnd_clean.tsv",
    "families_clean.tsv"
  )
  private val inputG2019sFileName = "lrrk2_clean.tsv"
  private val outputFileName = "all_merged.tsv"

  private val g2019sColumnName = "g2019s"

  private val delimiter = "\t"

  val headersWithContents = inputFileNames.map { fileName =>
    readFileAndAddEmptyColumn(folder + fileName)
  }

  val newG2019sHeaderWithContent = readFileAndMoveG2019sColumnToEnd(folder + inputG2019sFileName)

  val newHeader = headersWithContents.head._1 + delimiter + g2019sColumnName

  val mergedContent = (headersWithContents.map(_._2) ++ Seq(newG2019sHeaderWithContent)).mkString("\n")

  // write to file
  val pw = new PrintWriter(new File(folder + outputFileName))
  pw.write(newHeader + "\n")
  pw.write(mergedContent)
  pw.close

  def readFileAndAddEmptyColumn(fileName: String): (String, String) = {
    val lines = Source.fromFile(fileName).getLines()

    val header = lines.take(1).toSeq.head

    // new content
    val newContent = lines.map { line =>
      line + delimiter
    }.mkString("\n")
    (header, newContent)
  }

  def readFileAndMoveG2019sColumnToEnd(fileName: String): String = {
    val lines = Source.fromFile(fileName).getLines()

    val header = lines.take(1).toSeq.head

    // new content
    lines.map { line =>
      // sample	sex	aff	g2019s	aao	score	C1	C2	source
      val elements = line.split(delimiter, -1)

      val g2019s = elements(3)

      (elements.take(3).toSeq ++ elements.drop(4).toSeq ++ Seq(g2019s)).mkString(delimiter)
    }.mkString("\n")
  }
}