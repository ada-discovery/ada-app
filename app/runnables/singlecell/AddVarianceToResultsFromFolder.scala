package runnables.singlecell

import org.ada.server.AdaException
import org.apache.commons.lang3.StringEscapeUtils
import org.incal.core.runnables.{InputRunnable, InputRunnableExt}
import org.incal.core.util.{listFiles, writeStringAsStream}

import scala.io.Source
import scala.reflect.runtime.universe.typeOf

class AddVarianceToResultsFromFolder extends InputRunnableExt[AddVarianceToResultsFromFolderSpec] {

  private val defaultDelimiter = ","
  private val eol = "\n"

  override def run(
    input: AddVarianceToResultsFromFolderSpec
  ): Unit = {
    val delimiter = StringEscapeUtils.unescapeJava(input.delimiter.getOrElse(defaultDelimiter))

    val geneVarianceMap = loadGeneVariances(input.geneVarianceFileName, input.varianceDelimiter)

    // gene position file name -> variance sum calculation
    val cellPositionFileNames = listFiles(input.cellPositionFolderName).map(_.getName).filter(_.endsWith(input.extension))
    val cellPositionFileNameVarianceSumMap = cellPositionFileNames.map { cellPositionFileName =>
      val genesAsString = Source.fromFile(input.cellPositionFolderName + "/" + cellPositionFileName).getLines().next()

      val genes = genesAsString.split(delimiter, -1).map(_.trim)

      val varianceSum = genes.map(geneVarianceMap.get(_).get).sum

      (cellPositionFileName, varianceSum)
    }.toMap

    // add variance to summary results file and export
    val lines = Source.fromFile(input.inputSummaryResultsFileName).getLines()
    val newLines = lines.map { line =>
      val els = line.split(delimiter, -1).map(_.trim)
      val genesNum = els(0)
      val distance = els(1)
      val cellPositionFileName = els(2)

      val variance = cellPositionFileNameVarianceSumMap.get(cellPositionFileName).getOrElse(
        throw new AdaException(s"Cell position file name ${cellPositionFileName} not found.")
      )
      Seq(genesNum, distance, variance, cellPositionFileName).mkString(delimiter)
    }

    writeStringAsStream(newLines.mkString(eol), new java.io.File(input.exportSummaryResultsFileName))
  }

  private def loadGeneVariances(
    fileName: String,
    varianceDelimiter: Option[String]
  ): Map[String, Double] = {
    val delimiter = StringEscapeUtils.unescapeJava(varianceDelimiter.getOrElse(defaultDelimiter))

    val lines = Source.fromFile(fileName).getLines()
    val header = lines.next()

    lines.map { line =>
      val els = line.split(delimiter, -1).map(_.trim)
      val gene = els(0)
      val variance = els(1).toDouble
      (gene, variance)
    }.toMap
  }
}

case class AddVarianceToResultsFromFolderSpec(
  geneVarianceFileName: String,
  varianceDelimiter: Option[String],
  extension: String,
  cellPositionFolderName: String,
  inputSummaryResultsFileName: String,
  delimiter: Option[String],
  exportSummaryResultsFileName: String
)
