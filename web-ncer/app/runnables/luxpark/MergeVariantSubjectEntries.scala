package runnables.luxpark

import org.ada.server.{AdaException, AdaParseException}
import org.apache.commons.lang3.StringEscapeUtils
import org.incal.core.runnables.{InputRunnable, InputRunnableExt}
import org.incal.core.util.writeStringAsStream

import scala.io.Source

class MergeVariantSubjectEntries extends InputRunnableExt[MergeVariantSubjectEntriesSpec] {

  private val defaultDelimiter = "\t"
  private val booleanColumnNum = 75
  private val numericColumnNum = 3

  override def run(input: MergeVariantSubjectEntriesSpec) = {
    val delimiter = StringEscapeUtils.unescapeJava(input.delimiter.getOrElse(defaultDelimiter))

    val lines = Source.fromFile(input.subjectVariantInputFileName).getLines()
    val header = lines.next

    val aliquotIdLines = lines.zipWithIndex.map { case (line, variantIndex) =>
      val items = line.split(delimiter, -1).map(_.trim)

      val aliquotId	= items(0)

      (aliquotId, line)
    }.toSeq

    val existingAliquotIds = aliquotIdLines.map(_._1)
    val existingLines = aliquotIdLines.map(_._2).map(_ + delimiter + true)
    val allAliquotIds = Source.fromFile(input.fullAliquotIdsInputFileName).getLines().toSet

    val extraAliquotIds = allAliquotIds.--(existingAliquotIds)

    val newLines = extraAliquotIds.toSeq.sorted.map { aliquotId =>
      val kitId = aliquotId.substring(0, 15)

      (Seq(aliquotId, kitId) ++ Seq.fill(booleanColumnNum)("false") ++ Seq.fill(numericColumnNum)(0) ++ Seq(false)).mkString(delimiter)
    }

    val newHeader = header + delimiter + "Any_PD_Variant"
    val content = (Seq(newHeader) ++ existingLines ++ newLines).mkString("\n")
    writeStringAsStream(content, new java.io.File(outputFileName(input.subjectVariantInputFileName)))
  }

  private def outputFileName(inputFileName: String): String = {
    val inputFile = new java.io.File(inputFileName)
    if (!inputFile.exists)
      throw new AdaException(s"Input file '${inputFileName}' doesn't exist.")

    val (inputFileNamePrefix, extension) = if (inputFile.getName.contains('.')) {
      val parts = inputFile.getName.split("\\.", 2)
      (inputFile.getParent + "/" + parts(0), "." + parts(1))
    } else
      (inputFile.getName, "")

    inputFileNamePrefix + "-merged" + extension
  }
}

case class MergeVariantSubjectEntriesSpec(
  subjectVariantInputFileName: String,
  fullAliquotIdsInputFileName: String,
  delimiter: Option[String]
)