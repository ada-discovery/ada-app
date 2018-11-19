package runnables.singlecell

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import models.FieldTypeId
import org.apache.commons.lang3.StringEscapeUtils
import org.incal.core.InputFutureRunnable
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger

import collection.mutable.{ArrayBuffer, Map => MMap, Set => MSet}
import runnables.core.CalcUtil._
import services.stats.CalculatorExecutors
import org.incal.core.dataaccess.Criterion._
import util.writeByteArrayStream

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

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

  override def runAsFuture(input: CalcMCCBasedPositionsSpec) = {
    val delimiter = StringEscapeUtils.unescapeJava(input.delimiter.getOrElse(defaultDelimiter))
    val dsa = dsaf(input.dataSetId).get

    for {
      // get the boolean fields
      booleanFields <- dsa.fieldRepo.find(Seq("fieldType" #== FieldTypeId.Boolean))

      // sorted fields
      fieldsSeq = booleanFields.toSeq

      // calculate Matthews (binary class) correlations
      corrs <- matthewsBinaryClassCorrelationExec.execJsonRepoStreamed(
        Some(input.parallelism),
        Some(input.parallelism),
        true,
        fieldsSeq)(
        dsa.dataSetRepo, Nil
      )

    } yield {
      logger.info("MCC correlations calculated.")

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

        val cellName = indexFieldNameMap.get(cellIndex).get
        val bestPosName = indexFieldNameMap.get(bestPosIndex).get

        (cellName, bestPosName, maxCorr)
      }

      val lines = cellPositions.sortBy(_._1).map { case (cellName, bestPosName, corr) => Seq(cellName.substring(cellPrefixSize), bestPosName.substring(positionPrefixSize), corr).mkString(delimiter) }

      val outputStream = Stream((Seq(header.mkString(delimiter)) ++ lines).mkString(eol).getBytes(StandardCharsets.UTF_8))
      writeByteArrayStream(outputStream, new java.io.File(input.exportFileName))
    }
  }

  override def inputType = typeOf[CalcMCCBasedPositionsSpec]
}

case class CalcMCCBasedPositionsSpec(
  dataSetId: String,
  parallelism: Int,
  delimiter: Option[String],
  exportFileName: String
)