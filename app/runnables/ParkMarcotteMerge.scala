package runnables

import java.io._
import scala.io.Source
import util.getListOfFiles

object ParkMarcotteMerge extends App {
  val rootFolder = "/home/peter/Data/Park-Marcotte/human_random/"
  val folders = Seq("C1", "C2", "C3", "CV", "Train")
  val outputFile = "full-data-set.txt"

  val mergedContent = folders.map { folder =>
    val files = getListOfFiles(rootFolder + folder)
    files.map { file =>
      val lines = Source.fromFile(file).getLines()
      val setNumEnd = file.getName.indexOf('.')
      val setNumStart = file.getName.indexOf('_', setNumEnd - 4) + 1
      val setIndex = file.getName.substring(setNumStart, setNumEnd)

      // new content
      lines.map { line =>
        file.getName  + "\t" + folder + "\t" + setIndex + "\t" + line
      }.mkString("\n")
    }.mkString("\n")
  }.mkString("\n")

  val header = Seq("fileName", "group", "dataSetIndex", " interactionType", "protein1", "protein2").mkString("\t")

  // write to file
  val pw = new PrintWriter(new File(rootFolder + outputFile))
  pw.write(header + "\n")
  pw.write(mergedContent)
  pw.close
}
