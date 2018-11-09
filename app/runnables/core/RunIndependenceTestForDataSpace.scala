package runnables.core

import java.nio.charset.StandardCharsets
import javax.inject.Inject

import dataaccess.JsonUtil.jsonsToCsv
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import models.DataSetFormattersAndIds.FieldIdentity
import org.apache.commons.lang3.StringEscapeUtils
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import org.incal.core.InputFutureRunnable
import services.stats.StatsService
import services.stats.calc.{ChiSquareResult, OneWayAnovaResult}
import util.writeByteArrayStream

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RunIndependenceTestForDataSpace @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    statsService: StatsService
  ) extends InputFutureRunnable[RunIndependenceTestForDataSpaceSpec] {

  private val eol = "\n"
  private val headerColumnNames = Seq("dataSetId", "pValue", "fValue_or_statistics", "degreeOfFreedom", "testType")

  private val logger = Logger

  override def runAsFuture(
    input: RunIndependenceTestForDataSpaceSpec
  ) = {
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.exportDelimiter)

    for {
      dataSpace <- dataSpaceMetaInfoRepo.get(input.dataSpaceId)

      dataSetIds = dataSpace.map(_.dataSetMetaInfos.map(_.id)).getOrElse(Nil)

      outputs <- util.seqFutures(dataSetIds)(
        runIndependenceTest(input.inputFieldName, input.targetFieldName, unescapedDelimiter)
      )
    } yield {
      val header = headerColumnNames.mkString(unescapedDelimiter)
      val outputStream = Stream((Seq(header) ++ outputs).mkString(eol).getBytes(StandardCharsets.UTF_8))
      writeByteArrayStream(outputStream, new java.io.File(input.exportFileName))
    }
  }

  private def runIndependenceTest(
    inputFieldName: String,
    targetFieldName: String,
    delimiter: String)(
    dataSetId: String
  ): Future[String] = {
    logger.info(s"Running an independence test for the data set $dataSetId using the target field '$targetFieldName'.")
    val dsa = dsaf(dataSetId).get

    for {
      jsons <- dsa.dataSetRepo.find(projection = Seq(inputFieldName, targetFieldName))
      inputField <- dsa.fieldRepo.get(inputFieldName)
      targetField <- dsa.fieldRepo.get(targetFieldName)
    } yield
      statsService.testIndependence(jsons, Seq(inputField.get), targetField.get).head.map(
        _ match {
          case x: ChiSquareResult => Seq(dataSetId, x.pValue, x.statistics, x.degreeOfFreedom, "Chi-Square").mkString(delimiter)
          case x: OneWayAnovaResult => Seq(dataSetId, x.pValue, x.FValue, x.dfwg, "ANOVA").mkString(delimiter)
        }
      ).getOrElse(
        Seq(dataSetId, "", "", "").mkString(delimiter)
      )
  }

  override def inputType = typeOf[RunIndependenceTestForDataSpaceSpec]
}

case class RunIndependenceTestForDataSpaceSpec(
  dataSpaceId: BSONObjectID,
  inputFieldName: String,
  targetFieldName: String,
  exportFileName: String,
  exportDelimiter: String
)