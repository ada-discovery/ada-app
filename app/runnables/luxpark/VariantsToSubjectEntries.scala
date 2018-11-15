package runnables.luxpark

import models.{AdaException, AdaParseException}
import org.apache.commons.lang3.StringEscapeUtils
import org.incal.core.InputRunnable

import scala.io.Source
import util.{GroupMapList, writeStringAsStream}

import scala.reflect.runtime.universe.typeOf

class VariantsToSubjectEntries extends InputRunnable[VariantsToSubjectEntriesSpec] {

  private val defaultDelimiter = "\t"

  private val expectedColumnNames = Map(
    0 -> "Variant Internal Id",
    1 -> "Gene",
    2 -> "Category",
    3 -> "Variant",
    4 -> "Func.refGene",
    5 -> "ExonicFunc.refGene",
    6 -> "rsID",
    7 -> "number cases",
    8 -> "number controls",
    9 -> "hom case",
    10 -> "hom control",
    11 -> "case IDs",
    12 -> "control IDs"
  )

  private val newHeaderStart = Seq("Aliquot_Id", "Kit_Id")

  override def run(input: VariantsToSubjectEntriesSpec) = {
    val delimiter = StringEscapeUtils.unescapeJava(input.delimiter.getOrElse(defaultDelimiter))

    // retrieve output and label output file names
    val (outputFileName, labelOutputFileName) = outputFileNames(input.inputFileName)

    val lines = Source.fromFile(input.inputFileName).getLines()
    val header = lines.next

    // first validate column names
    val columnNames = header.split(delimiter, -1).map(_.trim)
    validateColumnNames(columnNames)

    val aliquotVariantEntries = lines.zipWithIndex.map { case (line, variantIndex) =>
      val items = line.split(delimiter, -1).map(_.trim)
      val variantInternalId = items(0)
      val gene = items(1)
      val category = items(2)
      val variants = items(3)

      val funcRefGene = items(4)
      val exonicFuncRefGene = items(5)

      val caseIds = items(11).split(",", -1).map(_.trim)
      val controlIds = items(12).split(",", -1).map(_.trim)

      val variantName = variants.split(",", -1).head.trim.replaceAllLiterally(".","_").replaceAllLiterally(":","_").replaceAllLiterally(">","_").replaceAllLiterally("+","_")

      val aliquotIds: Seq[String] = (caseIds ++ controlIds).filter(_.nonEmpty)

      val aliquotEntries = aliquotIds.map((_, variantIndex))
      val variantEntry = variantIndex -> (gene, category, funcRefGene, exonicFuncRefGene, variantName, variantInternalId)

      (aliquotEntries, variantEntry)
    }.toSeq

    val variantsCount = aliquotVariantEntries.size

    val variantIndexMap = aliquotVariantEntries.map(_._2).toMap

    val genesSorted = variantIndexMap.values.map(_._1).toSet.toSeq.sorted
    val categoriesSorted = variantIndexMap.values.map(_._2).toSet.toSeq.sorted
    val funcsSorted = variantIndexMap.values.map(_._3).toSet.toSeq.sorted
    val exonicFuncsSorted = variantIndexMap.values.map(_._4).toSet.toSeq.sorted

    val aliquotVariantIndexesMap = aliquotVariantEntries.flatMap(_._1).toGroupMap

    val newLines = aliquotVariantIndexesMap.toSeq.sortBy(_._1).map { case (aliquotId, variantIndexes) =>
      val kitId = aliquotId.substring(0, 15)

      val variantInfos = variantIndexes.map { variantIndex => variantIndexMap.get(variantIndex).get }

      val geneSet = variantInfos.map(_._1).toSet
      val categorySet = variantInfos.map(_._2).toSet
      val funcSet = variantInfos.map(_._3).toSet
      val exonicFuncSet = variantInfos.map(_._4).toSet

      val variantIndexSet = variantIndexes.toSet

      val variantColumns = (0 until variantsCount).map( index =>
        variantIndexSet.contains(index).toString
      )
      val geneColumns = genesSorted.map( gene =>
        geneSet.contains(gene).toString
      )
      val categoryColumns = categoriesSorted.map( category =>
        categorySet.contains(category).toString
      )
      val funcColumns = funcsSorted.map( func =>
        funcSet.contains(func).toString
      )
      val exonicFuncColumns = exonicFuncsSorted.map( exonicFunc =>
        exonicFuncSet.contains(exonicFunc).toString
      )

      (Seq(aliquotId, kitId) ++ variantColumns ++ geneColumns ++ categoryColumns ++ funcColumns ++ exonicFuncColumns).mkString(delimiter)
    }

    val variantInfos = aliquotVariantEntries.map(_._2._2)

    // subject variants
    val variantNames = variantInfos.map(_._5)
    val categoryLabels = categoriesSorted.map(_.capitalize + "_Variant")
    val funcLabels = funcsSorted.map(_.capitalize + "_Func_Variant")
    val exonicFuncLabels = exonicFuncsSorted.map(_.capitalize + "_ExonicFunc_Variant")

    val newHeader = newHeaderStart ++ variantNames ++ genesSorted ++ categoryLabels ++ funcLabels ++ exonicFuncLabels

    val content = newHeader.mkString(delimiter) + "\n" + newLines.mkString("\n")
    writeStringAsStream(content, new java.io.File(outputFileName))

    // variant name with labels
    val variantNameLabelLines = variantInfos.map(info => info._5 + delimiter + info._6)
    writeStringAsStream(variantNameLabelLines.mkString("\n"), new java.io.File(labelOutputFileName))
  }

  private def outputFileNames(inputFileName: String): (String, String) = {
    val inputFile = new java.io.File(inputFileName)
    if (!inputFile.exists)
      throw new AdaException(s"Input file '${inputFileName}' doesn't exist.")

    val (inputFileNamePrefix, extension) = if (inputFile.getName.contains('.')) {
      val parts = inputFile.getName.split("\\.", 2)
      (inputFile.getParent + "/" + parts(0), "." + parts(1))
    } else
      (inputFile.getName, "")

    val outputFileName = inputFileNamePrefix + "-by_subjects" + extension
    val labelOutputFileName = inputFileNamePrefix + "-labels" + extension

    (outputFileName, labelOutputFileName)
  }

  private def validateColumnNames(columnNames: Seq[String]) = {
    expectedColumnNames.map { case (index, expectedColumnName) =>
      if (index >= columnNames.size)
        throw new AdaParseException(s"Column $expectedColumnName at the position ${index + 1} expected but got only ${columnNames.size} columns.")
      else if (!columnNames(index).equals(expectedColumnName))
        throw new AdaParseException(s"Column $expectedColumnName at the position ${index + 1} expected but got ${columnNames(index)} instead.")
    }
  }

  override def inputType = typeOf[VariantsToSubjectEntriesSpec]
}

case class VariantsToSubjectEntriesSpec(
  inputFileName: String,
  delimiter: Option[String]
)