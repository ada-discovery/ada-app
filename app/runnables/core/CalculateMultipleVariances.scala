package runnables.core

import java.nio.charset.StandardCharsets
import javax.inject.Inject

import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import org.incal.core.dataaccess.Criterion._
import models.DataSetFormattersAndIds.FieldIdentity
import org.apache.commons.lang3.StringEscapeUtils
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import org.incal.core.InputFutureRunnable
import services.stats.{CalculatorExecutors, StatsService}
import services.stats.CalculatorHelper._
import util.writeByteArrayStream

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CalculateMultipleVariances @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    statsService: StatsService
  ) extends InputFutureRunnable[CalculateMultipleVariancesSpec] with CalculatorExecutors {

  private val eol = "\n"
  private val logger = Logger

  private val exec = multiBasicStatsSeqExec

  override def runAsFuture(
    input: CalculateMultipleVariancesSpec
  ) =
    calcVariances(input).map { lines =>
      val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.exportDelimiter)

      val header = Seq("targetFieldName", "variance").mkString(unescapedDelimiter)
      val outputStream = Stream((Seq(header) ++ lines).mkString(eol).getBytes(StandardCharsets.UTF_8))

      writeByteArrayStream(outputStream, new java.io.File(input.exportFileName))
    }

  private def calcVariances(
    input: CalculateMultipleVariancesSpec
  ): Future[Traversable[String]] = {

    logger.info(s"Calculating variances for the data set ${input.dataSetId} using the ${input.fieldNames.size} fields.")

    val dsa = dsaf(input.dataSetId).get
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.exportDelimiter)

    for {
      jsons <- dsa.dataSetRepo.find(projection = input.fieldNames)
      fields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> input.fieldNames))
    } yield {
      val sortedFields = fields.toSeq.sortBy(_.name)
      val stats = exec.execJson_(sortedFields)(jsons)

      stats.zip(sortedFields).map { case (stats, field) =>
        field.name + unescapedDelimiter + stats.map(_.variance.toString).getOrElse("")
      }
    }
  }

  override def inputType = typeOf[CalculateMultipleVariancesSpec]
}

case class CalculateMultipleVariancesSpec(
  dataSetId: String,
  fieldNames: Seq[String],
  exportFileName: String,
  exportDelimiter: String
)