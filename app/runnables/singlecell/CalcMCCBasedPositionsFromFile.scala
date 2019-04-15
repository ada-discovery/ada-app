package runnables.singlecell

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import org.ada.server.models.{Field, FieldTypeId}
import models.AdaException
import org.apache.commons.lang3.StringEscapeUtils
import org.incal.core.InputFutureRunnable
import org.incal.core.util.{writeStringAsStream, listFiles, seqFutures}

import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger

import collection.mutable.ArrayBuffer
import services.stats.CalculatorExecutors
import org.incal.core.dataaccess.Criterion._
import play.api.libs.json.Json

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

/**
  * Runnable to calculate MCC-based cell positions for given genes (provided in a file)
  *
  * @param dsaf
  */
class CalcMCCBasedPositionsFromFile @Inject()(
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[CalcMCCBasedPositionsFromFileSpec] with CalcMCCBasedPositionsHelper {

  protected implicit val positionInfoFormat = Json.format[PositionInfo]
  protected implicit val goldStandardCellPositionInfoFormat = Json.format[GoldStandardCellPositionInfo]

  override def runAsFuture(input: CalcMCCBasedPositionsFromFileSpec) = {
    val cellPositionGeneDsa = dsaf(input.cellPositionGeneDataSetId).get
    val goldStandardPositionDsa = dsaf(input.goldStandardPositionDataSetId).get
    val spatialPositionDsa = dsaf(input.spatialPositionDataSetId).get

    for {
      // get the boolean fields
      booleanFields <- cellPositionGeneDsa.fieldRepo.find(Seq("fieldType" #== FieldTypeId.Boolean))

      // sorted fields
      fieldsSeq = booleanFields.toSeq

      // position infos
      positionInfos <- spatialPositionDsa.dataSetRepo.find().map(_.map(_.as[PositionInfo]))

      // gold standard cell position infos
      goldStandardCellPositionInfos <- goldStandardPositionDsa.dataSetRepo.find().map(_.map(_.as[GoldStandardCellPositionInfo]))

      // find cell positions and return the mean distance
      meanDistance <- if (input.nonUniqueMethod)
          matchCellPositionsNonUnique(
            cellPositionGeneDsa, positionInfos, goldStandardCellPositionInfos, fieldsSeq, input.geneSelection, input.delimiter,
            input.parallelism, input.exportFileName, input.nonUniqueMethodPositionSelectionNum.getOrElse(10)
          )
        else
          matchCellPositionsUnique(
            cellPositionGeneDsa, positionInfos, goldStandardCellPositionInfos, fieldsSeq, input.geneSelection, input.delimiter,
            input.parallelism, input.exportFileName
          )

    } yield {
      // export the meta infos
      val metaInfoContent = Seq("Genes: " + input.geneSelection.mkString(", "), "Mean Distance: " + meanDistance).mkString(eol)
      writeStringAsStream(metaInfoContent, new java.io.File(input.exportFileName + "_meta"))
    }
  }

  override def inputType = typeOf[CalcMCCBasedPositionsFromFileSpec]
}


/**
  * Batch runnable to calculate MCC-based cell positions for given genes (provided as files in a given folder)
  *
  * @param dsaf
  */
class CalcMCCBasedPositionsFromFolder @Inject()(
  dsaf: DataSetAccessorFactory
) extends InputFutureRunnable[CalcMCCBasedPositionsFromFolderSpec] with CalcMCCBasedPositionsHelper {

  protected implicit val positionInfoFormat = Json.format[PositionInfo]
  protected implicit val goldStandardCellPositionInfoFormat = Json.format[GoldStandardCellPositionInfo]

  override def runAsFuture(input: CalcMCCBasedPositionsFromFolderSpec) = {
    val cellPositionGeneDsa = dsaf(input.cellPositionGeneDataSetId).get
    val goldStandardPositionDsa = dsaf(input.goldStandardPositionDataSetId).get
    val spatialPositionDsa = dsaf(input.spatialPositionDataSetId).get

    for {
    // get the boolean fields
      booleanFields <- cellPositionGeneDsa.fieldRepo.find(Seq("fieldType" #== FieldTypeId.Boolean))

      // sorted fields
      fieldsSeq = booleanFields.toSeq

      // position infos
      positionInfos <- spatialPositionDsa.dataSetRepo.find().map(_.map(_.as[PositionInfo]))

      // gold standard cell position infos
      goldStandardCellPositionInfos <- goldStandardPositionDsa.dataSetRepo.find().map(_.map(_.as[GoldStandardCellPositionInfo]))

      // find cell positions for all cells in the folds
      results <- runForFolderAux(
        cellPositionGeneDsa, positionInfos, goldStandardCellPositionInfos, fieldsSeq, input.delimiter,
        input.parallelism, input.inputFolderName, input.extension, input.exportFolderName, input.nonUniqueMethod, input.nonUniqueMethodPositionSelectionNum.getOrElse(10)
      )
    } yield {
      logger.info(s"${results.size} MCC-based cell-locations for the folder ${input.inputFolderName} finished.")

      // export the results (summary)
      val resultsSorted = results.toSeq.sortWith { case ((genesNum1, distance1, _), (genesNum2, distance2, _)) =>
        if (genesNum1 == genesNum2) distance1 < distance2 else genesNum1 < genesNum2
      }

      val content = resultsSorted.map { case (genesNum, distance, file) => Seq(genesNum, distance, file).mkString(", ")}.mkString(eol)
      writeStringAsStream(content, new java.io.File(input.exportFolderName  + "/" + "cell_positions_results.csv"))
    }
  }

  private def runForFolderAux(
    cellPositionGeneDsa: DataSetAccessor,
    positionInfos: Traversable[PositionInfo],
    goldStandardCellPositionInfos: Traversable[GoldStandardCellPositionInfo],
    fieldsSeq: Seq[Field],
    delimiterOption: Option[String],
    parallelism: Int,
    inputFolderName: String,
    extension: String,
    exportFolderName: String,
    finalMethod: Boolean,
    finalMethodPositionSelectionNum: Int
  ): Future[Traversable[(Int, Double, String)]] = {
    val inputFileNames = listFiles(inputFolderName).map(_.getName).filter(_.endsWith(extension))
    val delimiter = StringEscapeUtils.unescapeJava(delimiterOption.getOrElse(defaultDelimiter))


    seqFutures(inputFileNames) { inputFileName =>
      val selectedGenes = Source.fromFile(inputFolderName + "/" + inputFileName).mkString.split(delimiter, -1).toSeq
      val exportFileName = inputFileName.substring(0, inputFileName.size - (extension.size + 1)) + "_cell_positions.csv"
      val fullExportFileName = exportFolderName + "/" + exportFileName

      // find cell positions and return the mean distance
      val distanceFuture =
        if (finalMethod)
          matchCellPositionsNonUnique(
            cellPositionGeneDsa, positionInfos, goldStandardCellPositionInfos, fieldsSeq, selectedGenes, delimiterOption, parallelism, fullExportFileName, finalMethodPositionSelectionNum
          )
        else
          matchCellPositionsUnique(
            cellPositionGeneDsa, positionInfos, goldStandardCellPositionInfos, fieldsSeq, selectedGenes, delimiterOption, parallelism, fullExportFileName
          )

      distanceFuture.map { meanDistance =>
        (selectedGenes.size, meanDistance, exportFileName)
      }
    }
  }

  override def inputType = typeOf[CalcMCCBasedPositionsFromFolderSpec]
}


/**
  * Helper trait with two calculation methods: unique position and non-unique positions, both maximizing MCC
  */
trait CalcMCCBasedPositionsHelper extends CalculatorExecutors {

  protected val logger = Logger

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private val positionPrefix = "pos-"
  private val cellPrefix = "cell-"
  private val cellPrefixSize = cellPrefix.length
  private val positionPrefixSize = positionPrefix.length

  protected val defaultDelimiter = ","
  protected val eol = "\n"

  // returns the mean distance
  protected def matchCellPositionsUnique(
    cellPositionGeneDsa: DataSetAccessor,
    positionInfos: Traversable[PositionInfo],
    goldStandardCellPositionInfos: Traversable[GoldStandardCellPositionInfo],
    fieldsSeq: Seq[Field],
    selectedGenes: Seq[String],
    delimiterOption: Option[String],
    parallelism: Int,
    exportFileName: String
  ): Future[Double] = {
    val delimiter = StringEscapeUtils.unescapeJava(delimiterOption.getOrElse(defaultDelimiter))

    val geneCriteria = if (selectedGenes.nonEmpty) Seq("Gene" #-> selectedGenes) else Nil

    for {
      // get all the referenced gene names
      foundGenes <- cellPositionGeneDsa.dataSetRepo.find(criteria = geneCriteria, projection = Seq("Gene")).map(_.map(json => (json \ "Gene").as[String]))

      // calculate Matthews (binary class) correlations
      corrs <- matthewsBinaryClassCorrelationExec.execJsonRepoStreamed(
        Some(parallelism),
        Some(parallelism),
        true,
        fieldsSeq)(
        cellPositionGeneDsa.dataSetRepo,
        geneCriteria
      )

    } yield {
      logger.info("MCC correlations calculated.")

      if (selectedGenes.nonEmpty)
        if (foundGenes.size < selectedGenes.size) {
          val notFoundGenes = selectedGenes.toSet.--(foundGenes)
          throw new AdaException(s"Gene(s) '${notFoundGenes.mkString(",")}' not found.")
        }

      val positionMap = positionInfos.map(info => (info.id, info)).toMap
      val goldStandardCellPositionMap = goldStandardCellPositionInfos.map(info => (info.Cell, info)).toMap

      val fieldNames = fieldsSeq.map(_.name)

      val fieldNameIndeces = fieldNames.zipWithIndex
      val indexFieldNameMap = fieldNameIndeces.map(_.swap).toMap

      def filterIndecesByPrefix(prefix: String): ArrayBuffer[Int] = {
        val indeces = fieldNameIndeces.filter { case (name, _) => name.startsWith(prefix) }.map(_._2)
        ArrayBuffer(indeces: _*)
      }

      val cellIndeces = filterIndecesByPrefix(cellPrefix)
      val posIndeces = filterIndecesByPrefix(positionPrefix)

      val cellNum = cellIndeces.size

      logger.info(s"Finding locations for $cellNum cells.")

      val cellPositions = for (_ <- 0 until cellNum) yield {

        val cellMaxCorrs = cellIndeces.toSeq.map { cellIndex =>
          val cellCorrs = corrs(cellIndex)

          val (maxCorr, bestPosIndex) = posIndeces.map(posIndex => (cellCorrs(posIndex), posIndex)).collect { case (Some(corr), index) => (corr, index) }.maxBy(_._1)
          (cellIndex, bestPosIndex, maxCorr)
        }

        val (cellIndex, bestPosIndex, corr) = cellMaxCorrs.maxBy(_._3)

        cellIndeces.-=(cellIndex)
        posIndeces.-=(bestPosIndex)

        val cellName = indexFieldNameMap.get(cellIndex).get.substring(cellPrefixSize)
        val bestPosId = indexFieldNameMap.get(bestPosIndex).get.substring(positionPrefixSize)

        val cellPosition = positionMap.get(bestPosId.toInt).get
        val idealCellPosition = goldStandardCellPositionMap.get(cellName).get

        val dist = euclideanDistance(cellPosition, idealCellPosition)

        (cellName, bestPosId, dist, corr)
      }

      assert(cellPositions.size == cellNum, s"$cellNum cell positions expected but got ${cellPositions.size}.")

      val meanDistance = cellPositions.map(_._3).sum / cellNum
      val meanCorrelation = cellPositions.map(_._4).sum / cellNum

      logger.info(s"MCC-based cell-location matching finished with a mean distance of $meanDistance and a mean MCC of $meanCorrelation.")

      // export the gene locations
      val lines = cellPositions.toSeq.sortBy(_._1).map { case (cellName, bestPosId, _, _) => Seq(cellName, bestPosId).mkString(delimiter) }
      val content = (Seq(foundGenes.toSeq.sorted.mkString(delimiter)) ++ lines).mkString(eol)
      writeStringAsStream(content, new java.io.File(exportFileName))

      meanDistance
    }
  }

  // returns the mean distance
  protected def matchCellPositionsNonUnique(
    cellPositionGeneDsa: DataSetAccessor,
    positionInfos: Traversable[PositionInfo],
    goldStandardCellPositionInfos: Traversable[GoldStandardCellPositionInfo],
    fieldsSeq: Seq[Field],
    selectedGenes: Seq[String],
    delimiterOption: Option[String],
    parallelism: Int,
    exportFileName: String,
    positionSelectionNum: Int
  ): Future[Double] = {
    val delimiter = StringEscapeUtils.unescapeJava(delimiterOption.getOrElse(defaultDelimiter))

    val geneCriteria = if (selectedGenes.nonEmpty) Seq("Gene" #-> selectedGenes) else Nil

    for {
      // get all the referenced gene names
      foundGenes <- cellPositionGeneDsa.dataSetRepo.find(criteria = geneCriteria, projection = Seq("Gene")).map(_.map(json => (json \ "Gene").as[String]))

      // calculate Matthews (binary class) correlations
      corrs <- matthewsBinaryClassCorrelationExec.execJsonRepoStreamed(
        Some(parallelism),
        Some(parallelism),
        true,
        fieldsSeq)(
        cellPositionGeneDsa.dataSetRepo,
        geneCriteria
      )

    } yield {
      logger.info("MCC correlations calculated.")

      if (selectedGenes.nonEmpty)
        if (foundGenes.size < selectedGenes.size) {
          val notFoundGenes = selectedGenes.toSet.--(foundGenes)
          throw new AdaException(s"Gene(s) '${notFoundGenes.mkString(",")}' not found.")
        }

      val positionMap = positionInfos.map(info => (info.id, info)).toMap
      val goldStandardCellPositionMap = goldStandardCellPositionInfos.map(info => (info.Cell, info)).toMap

      val fieldNames = fieldsSeq.map(_.name)

      val fieldNameIndeces = fieldNames.zipWithIndex
      val indexFieldNameMap = fieldNameIndeces.map(_.swap).toMap

      def filterIndecesByPrefix(prefix: String): Seq[Int] = {
        fieldNameIndeces.filter { case (name, _) => name.startsWith(prefix) }.map(_._2)
      }

      val cellIndeces = filterIndecesByPrefix(cellPrefix)
      val posIndeces = filterIndecesByPrefix(positionPrefix)

      val cellNum = cellIndeces.size

      logger.info(s"Finding locations for $cellNum cells.")

      val cellPositions = cellIndeces.map { cellIndex =>
        val cellName = indexFieldNameMap.get(cellIndex).get.substring(cellPrefixSize)
        val cellCorrs = corrs(cellIndex)

        val bestPositionInfos = posIndeces.map(posIndex => (cellCorrs(posIndex), posIndex)).collect { case (Some(corr), posIndex) =>
          val posId = indexFieldNameMap.get(posIndex).get.substring(positionPrefixSize).toInt
          (corr, posId)
        }.sortBy(-_._1).take(positionSelectionNum)

        val idealCellPosition = goldStandardCellPositionMap.get(cellName).get

        val dists = bestPositionInfos.map { case (_, posId) =>
          val position = positionMap.get(posId).get
          euclideanDistance(position, idealCellPosition)
        }

        val bestCorrs = bestPositionInfos.map(_._1)
        val bestPositions = bestPositionInfos.map(_._2)

        (cellName, bestPositions, dists.sum / dists.size, bestCorrs.sum / bestCorrs.size)
      }

      assert(cellPositions.size == cellNum, s"$cellNum cell positions expected but got ${cellPositions.size}.")

      val meanDistance = cellPositions.map(_._3).sum / cellNum
      val meanCorrelation = cellPositions.map(_._4).sum / cellNum

      logger.info(s"MCC-based cell-location matching finished with a mean distance of $meanDistance and a mean MCC of $meanCorrelation.")

      // export the gene locations
      val lines = cellPositions.sortBy(_._1).map { case (cellName, positions, _, _) => (Seq(cellName) ++ positions).mkString(delimiter) }

      val content = (Seq(foundGenes.toSeq.sorted.mkString(delimiter)) ++ lines).mkString(eol)
      writeStringAsStream(content, new java.io.File(exportFileName))

      meanDistance
    }
  }

  private def euclideanDistance(
    position: PositionInfo,
    idealPosition: GoldStandardCellPositionInfo
  ) = {
    val xDiff = idealPosition.xcoord - position.xcoord
    val yDiff = idealPosition.ycoord - position.ycoord
    val zDiff = idealPosition.zcoord - position.zcoord

    Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff)
  }
}

case class CalcMCCBasedPositionsFromFileSpec(
  cellPositionGeneDataSetId: String,
  goldStandardPositionDataSetId: String,
  spatialPositionDataSetId: String,
  geneSelection: Seq[String],
  delimiter: Option[String],
  exportFileName: String,
  parallelism: Int,
  nonUniqueMethod: Boolean,
  nonUniqueMethodPositionSelectionNum: Option[Int]
)

case class CalcMCCBasedPositionsFromFolderSpec(
  cellPositionGeneDataSetId: String,
  goldStandardPositionDataSetId: String,
  spatialPositionDataSetId: String,
  inputFolderName: String,
  extension: String,
  delimiter: Option[String],
  exportFolderName: String,
  parallelism: Int,
  nonUniqueMethod: Boolean,
  nonUniqueMethodPositionSelectionNum: Option[Int]
)

case class PositionInfo(
  id: Int,
  xcoord: Double,
  ycoord: Double,
  zcoord: Double
)

case class GoldStandardCellPositionInfo(
  Cell: String,
  Position: Int,
  xcoord: Double,
  ycoord: Double,
  zcoord: Double
)