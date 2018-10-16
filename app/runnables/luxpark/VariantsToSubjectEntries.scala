package runnables.luxpark

import scala.io.Source
import util.{writeStringAsStream, GroupMapList}

object VariantsToSubjectEntries extends App {

  private val inputFileName = "/home/peter/Data/LuxPark_Neurochip/variants2ada.csv"
  private val outputFileName = "/home/peter/Data/LuxPark_Neurochip/variants2ada-by_subjects.csv"
  private val labelOutputFileName = "/home/peter/Data/LuxPark_Neurochip/variantLabels.csv"

  private val delimiter = "\t"

  // Variant Internal Id, Gene, Category, Variant,	Func.refGene,	ExonicFunc.refGene,	rsID,	number cases,	number controls,	hom case,	hom control,	case IDs,	control IDs

  private val newHeaderStart = Seq("Aliquot_Id", "Kit_Id")

  process

  private def process = {
    val lines = Source.fromFile(inputFileName).getLines()
    val header = lines.next

    val aliquotVariantEntries = lines.zipWithIndex.map { case (line, variantIndex) =>
      val items = line.split("\t", -1).map(_.trim)
      val variantInternalId = items(0)
      val gene = items(1)
      val category = items(2)
      val variants = items(3)
      val caseIds = items(11).split(",", -1).map(_.trim)
      val controlIds = items(12).split(",", -1).map(_.trim)

      val variantName = variants.split(",", -1).head.trim.replaceAllLiterally(".","_").replaceAllLiterally(":","_").replaceAllLiterally(">","_").replaceAllLiterally("+","_")

      val aliquotIds: Seq[String] = (caseIds ++ controlIds).filter(_.nonEmpty)

      val aliquotEntries = aliquotIds.map((_, variantIndex))
      val variantEntry = variantIndex -> (gene, category, variantName, variantInternalId)

      (aliquotEntries, variantEntry)
    }.toSeq

    val variantsCount = aliquotVariantEntries.size

    val variantIndexMap = aliquotVariantEntries.map(_._2).toMap

    val genesSorted = variantIndexMap.values.map(_._1).toSet.toSeq.sorted
    val categoriesSorted = variantIndexMap.values.map(_._2).toSet.toSeq.sorted

    val aliquotVariantIndexesMap = aliquotVariantEntries.flatMap(_._1).toGroupMap

    val newLines = aliquotVariantIndexesMap.toSeq.sortBy(_._1).map { case (aliquotId, variantIndexes) =>
      val kitId = aliquotId.substring(0, 15)

      val variantInfos = variantIndexes.map { variantIndex => variantIndexMap.get(variantIndex).get }

      val geneSet = variantInfos.map(_._1).toSet
      val categorySet = variantInfos.map(_._2).toSet
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
      (Seq(aliquotId, kitId) ++ variantColumns ++ geneColumns ++ categoryColumns).mkString(delimiter)
    }

    val variantInfos = aliquotVariantEntries.map(_._2._2)

    // subject variants
    val variantNames = variantInfos.map(_._3)
    val categoryLabels = categoriesSorted.map(category => category.capitalize + "_Variant")
    val newHeader = newHeaderStart ++ variantNames ++ genesSorted ++ categoryLabels

    val content = newHeader.mkString(delimiter) + "\n" + newLines.mkString("\n")
    writeStringAsStream(content, new java.io.File(outputFileName))

    // variant name with labels
    val variantNameLabelLines = variantInfos.map(info => info._3 + delimiter + info._4)
    writeStringAsStream(variantNameLabelLines.mkString("\n"), new java.io.File(labelOutputFileName))
  }
}
