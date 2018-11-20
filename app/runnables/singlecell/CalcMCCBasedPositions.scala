package runnables.singlecell

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import models.{AdaException, FieldTypeId}
import org.apache.commons.lang3.StringEscapeUtils
import org.incal.core.InputFutureRunnable
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger

import collection.mutable.{ArrayBuffer, Map => MMap, Set => MSet}
import runnables.core.CalcUtil._
import services.stats.CalculatorExecutors
import org.incal.core.dataaccess.Criterion._
import play.api.libs.json.Json
import util.writeStringAsStream

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CalcMCCBasedPositions @Inject()(
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[CalcMCCBasedPositionsSpec] with CalculatorExecutors {

  private val logger = Logger

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()
  private val defaultDelimiter = ","

  private val positionPrefix = "pos-"
  private val cellPrefix = "cell-"
  private val cellPrefixSize = cellPrefix.length
  private val positionPrefixSize = positionPrefix.length

  private val header = Seq("Cell", "Position", "MCC")
  private val eol = "\n"

  private implicit val positionInfoFormat = Json.format[PositionInfo]
  private implicit val goldStandardCellPositionInfoFormat = Json.format[GoldStandardCellPositionInfo]

  override def runAsFuture(input: CalcMCCBasedPositionsSpec) = {
    val delimiter = StringEscapeUtils.unescapeJava(input.delimiter.getOrElse(defaultDelimiter))
    val cellPositionGeneDsa = dsaf(input.cellPositionGeneDataSetId).get
    val goldStandardPositionDsa = dsaf(input.goldStandardPositionDataSetId).get
    val spatialPositionDsa = dsaf(input.spatialPositionDataSetId).get

    val geneCriteria = if (input.geneSelection.nonEmpty) Seq("Gene" #-> input.geneSelection) else Nil

    for {
      // get the boolean fields
      booleanFields <- cellPositionGeneDsa.fieldRepo.find(Seq("fieldType" #== FieldTypeId.Boolean))

      // sorted fields
      fieldsSeq = booleanFields.toSeq

      // position infos
      positionInfos <- spatialPositionDsa.dataSetRepo.find().map(_.map(_.as[PositionInfo]))

      // gold standard cell position infos
      goldStandardCellPositionInfos <- goldStandardPositionDsa.dataSetRepo.find().map(_.map(_.as[GoldStandardCellPositionInfo]))

      // get all the referenced gene names
      foundGenes <- cellPositionGeneDsa.dataSetRepo.find(criteria = geneCriteria, projection = Seq("Gene")).map(_.map(json => (json \ "Gene").as[String]))

      // calculate Matthews (binary class) correlations
      corrs <- matthewsBinaryClassCorrelationExec.execJsonRepoStreamed(
        Some(input.parallelism),
        Some(input.parallelism),
        true,
        fieldsSeq)(
        cellPositionGeneDsa.dataSetRepo,
        geneCriteria
      )

    } yield {
      logger.info("MCC correlations calculated.")

      if (input.geneSelection.nonEmpty)
        if (foundGenes.size < input.geneSelection.size) {
          val notFoundGenes = input.geneSelection.toSet.--(foundGenes)
          throw new AdaException(s"Gene(s) '${notFoundGenes.mkString(",")}' not found.")
        }

      val positionMap = positionInfos.map(info => (info.id, info)).toMap
      val goldStandardCellPositionMap = goldStandardCellPositionInfos.map(info => (info.Cell, info)).toMap

      val fieldNames = fieldsSeq.map(_.name)

      val fieldNameIndeces = fieldNames.zipWithIndex
      val indexFieldNameMap = fieldNameIndeces.map(_.swap).toMap

      def filterIndecesByPrefix(prefix: String): ArrayBuffer[Int] = {
        val indeces = fieldNameIndeces.filter { case (name, _) => name.startsWith(prefix) }.map(_._2)
        ArrayBuffer(indeces :_*)
      }

      val cellIndeces = filterIndecesByPrefix(cellPrefix)
      val posIndeces = filterIndecesByPrefix(positionPrefix)

      val cellNum = cellIndeces.size

      logger.info(s"Finding locations for $cellNum cells.")

      val cellPositions = for (_ <- 0 until cellNum) yield {

        val cellMaxCorrs = cellIndeces.toSeq.map { cellIndex =>
          val cellCorrs = corrs(cellIndex)

          val (maxCorr, bestPosIndex) = posIndeces.map( posIndex => (cellCorrs(posIndex), posIndex)).collect{ case (Some(corr), index) => (corr, index) }.maxBy(_._1)
          (cellIndex, bestPosIndex, maxCorr)
        }

        val (cellIndex, bestPosIndex, maxCorr) = cellMaxCorrs.maxBy(_._3)

        cellIndeces.-=(cellIndex)
        posIndeces.-=(bestPosIndex)

        val cellName = indexFieldNameMap.get(cellIndex).get.substring(cellPrefixSize)
        val bestPosId = indexFieldNameMap.get(bestPosIndex).get.substring(positionPrefixSize)

        val cellPosition = positionMap.get(bestPosId.toInt).get
        val idealCellPosition = goldStandardCellPositionMap.get(cellName).get

        val dist = euclideanDistance(cellPosition, idealCellPosition)

        (cellName, bestPosId, maxCorr, dist)
      }

      val meanDistance = cellPositions.map(_._4).sum / cellNum

      logger.info(s"MCC-based cell-location matching finished with a mean distance of $meanDistance.")

      // export the gene locations
      val lines = cellPositions.sortBy(_._1).map { case (cellName, bestPosId, corr, _) => Seq(cellName, bestPosId, corr).mkString(delimiter) }
      val content = (Seq(header.mkString(delimiter)) ++ lines).mkString(eol)
      writeStringAsStream(content, new java.io.File(input.exportFileName))

      // export the meta infos
      val metaInfoContent = Seq("Genes: " + foundGenes.mkString(", "), "Distance: " + meanDistance).mkString(eol)
      writeStringAsStream(metaInfoContent, new java.io.File(input.exportFileName + "_meta"))
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

  override def inputType = typeOf[CalcMCCBasedPositionsSpec]
}

case class CalcMCCBasedPositionsSpec(
  cellPositionGeneDataSetId: String,
  goldStandardPositionDataSetId: String,
  spatialPositionDataSetId: String,
  geneSelection: Seq[String],
  parallelism: Int,
  delimiter: Option[String],
  exportFileName: String
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