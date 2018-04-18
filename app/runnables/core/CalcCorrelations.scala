package runnables.core

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.{util => ju}

import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.util.ByteString
import com.google.inject.Inject
import dataaccess.Criterion._
import models.DataSetFormattersAndIds.FieldIdentity
import models.{Field, FieldTypeId}
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import runnables.InputFutureRunnable
import services.stats.StatsService
import util.{seqFutures, writeByteArrayStream}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class CalcCorrelations @Inject()(
    dsaf: DataSetAccessorFactory,
    statsService: StatsService
  ) extends InputFutureRunnable[CalcCorrelationsSpec] {

  private val logger = Logger

  import statsService._

  def runAsFuture(input: CalcCorrelationsSpec) = {
    val dsa = dsaf(input.dataSetId).get
    val dataSetRepo = dsa.dataSetRepo

    for {
      // get the fields first
      numericFields <-
        if (input.featuresNum.isDefined)
          dsa.fieldRepo.find(
            Seq("fieldType" #-> Seq(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Date)),
            limit = Some(input.featuresNum.get)
          )
        else
          dsa.fieldRepo.find(
            Seq(
              "fieldType" #-> Seq(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Date),
              FieldIdentity.name #!-> input.allFeaturesExcept
            )
          )

      // sorted fields
      sortedFields = numericFields.toSeq.sortBy(_.name)
      fieldNames = sortedFields.map(_.name)

      (correlations, execTime) <- {
        val calcStart = new ju.Date
        seqFutures(1 to input.standardRepetitions) { _ =>
          for {
            items <- dataSetRepo.find(projection = fieldNames)
          } yield
            pearsonCorrelationExec.execJson((), sortedFields)(items)
        }.map { results =>
          if (results.isEmpty) {
            (Nil, 0)
          } else {
            val execTime = new ju.Date().getTime - calcStart.getTime
            (results.head, execTime.toDouble / (1000 * input.standardRepetitions))
          }
        }
      }

      (streamedCorrelations, streamExecTime) <- {
        val calcStart = new ju.Date
        seqFutures(1 to input.streamRepetitions) { _ =>
          calcPearsonCorrelationsStreamed(dataSetRepo, Nil, sortedFields, input.streamParallelism, input.streamWithProjection, input.streamAreValuesAllDefined)
        }.map { results =>
          val execTime = new ju.Date().getTime - calcStart.getTime
          (results.head, execTime.toDouble / (1000 * input.streamRepetitions))
        }
      }
    } yield {
      logger.info(s"Correlation for ${numericFields.size} fields using ALL DATA finished in ${execTime} sec on average.")
      logger.info(s"Correlation for ${numericFields.size} fields using STREAMS finished in ${streamExecTime} sec on average.")

      correlations.zip(streamedCorrelations).map { case (rowCor1, rowCor2) =>
        rowCor1.zip(rowCor2).map { case (cor1, cor2) =>
          assert(cor1.equals(cor2), s"$cor1 is not equal $cor2.")
        }
      }

      val correlationsToExport = if (correlations.nonEmpty) correlations else streamedCorrelations
      input.exportFileName.map { exportFileName =>
        logger.info(s"Exporting the calculated correlations to $exportFileName.")
        FeatureMatrixIO.saveSquare(
          correlationsToExport,
          sortedFields.map(_.name),
          exportFileName,
          (value: Option[Double]) => value.map(_.toString).getOrElse("")
        )
      }.getOrElse(
        ()
      )
    }
  }

  override def inputType = typeOf[CalcCorrelationsSpec]
}

case class CalcCorrelationsSpec(
  dataSetId: String,
  featuresNum: Option[Int],
  allFeaturesExcept: Seq[String],
  standardRepetitions: Int,
  streamRepetitions: Int,
  streamParallelism: Option[Int],
  streamWithProjection: Boolean,
  streamAreValuesAllDefined: Boolean,
  exportFileName: Option[String]
)

object FeatureMatrixExport {


}