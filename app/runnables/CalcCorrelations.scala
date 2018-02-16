package runnables

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.{util => ju}

import com.google.inject.Inject
import persistence.dataset.DataSetAccessorFactory
import util.{seqFutures, writeByteArrayStream}
import dataaccess.Criterion._
import models.DataSetFormattersAndIds.FieldIdentity
import models.{Field, FieldTypeId}
import org.apache.commons.io.IOUtils
import play.api.Logger
import services.stats.StatsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CalcCorrelations @Inject()(
    dsaf: DataSetAccessorFactory,
    statsService: StatsService
  ) extends InputFutureRunnable[CalcCorrelationsSpec] {

  private val logger = Logger

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
            statsService.calcPearsonCorrelations(items, sortedFields)
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
          statsService.calcPearsonCorrelationsStreamed(dataSetRepo, Nil, sortedFields, input.streamParallelism, input.streamWithProjection, input.streamAreValuesAllDefined)
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
        exportCorrelationsOptimal(correlationsToExport, sortedFields, exportFileName)
      }.getOrElse(
        ()
      )
    }
  }

  private def exportCorrelationsOptimal(
    corrs: Seq[Seq[Option[Double]]],
    fields: Seq[Field],
    fileName: String
  ) = {
    val fieldNames = fields.map(field => field.name.replaceAllLiterally("u002e", "."))

    val header = "featureName," + fieldNames.mkString(",") + "\n"
    val headerBytes = header.getBytes(StandardCharsets.UTF_8)

    val rowBytesStream = (corrs.toStream, fieldNames).zipped.toStream.map { case (rowCorrs, fieldName) =>
      val rowValues = rowCorrs.map(_.map(_.toString).getOrElse("")).mkString(",")
      val rowContent = fieldName + "," + rowValues + "\n"
      rowContent.getBytes(StandardCharsets.UTF_8)
    }

    val outputStream = Stream(headerBytes) #::: rowBytesStream

    writeByteArrayStream(outputStream, new java.io.File(fileName))
  }

  @Deprecated
  private def exportCorrelations(
    corrs: Seq[Seq[Option[Double]]],
    fields: Seq[Field],
    fileName: String
  ) = {
    val fieldNames = fields.map(field => field.name.replaceAllLiterally("u002e", "."))
    val rows = corrs.zip(fieldNames).map { case (rowCorrs, fieldName) =>
      val rowValues = rowCorrs.map(_.map(_.toString).getOrElse("")).mkString(",")
      fieldName + "," + rowValues
    }

    val header = "featureName," + fieldNames.mkString(",")
    val content = (Seq(header) ++ rows).mkString("\n")

    Files.write(
      Paths.get(fileName),
      content.getBytes(StandardCharsets.UTF_8)
    )
    ()
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