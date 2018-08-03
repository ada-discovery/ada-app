package runnables.core

import javax.inject.Inject

import com.banda.core.plotter.{Plotter, SeriesPlotSetting}
import models.ml.{LearningSetting, RegressionEvalMetric, VectorTransformType}
import persistence.RepoTypes.RegressionRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import reactivemongo.bson.BSONObjectID
import runnables.InputFutureRunnable
import services.ml.{MachineLearningService, MachineLearningUtil}
import util.writeStringAsStream

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.Future

class RunDelayLineSeriesRegression @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    regressionRepo: RegressionRepo
  ) extends InputFutureRunnable[DelayLineRegressionSetting] {

  private val plotter = Plotter("svg")

  override def runAsFuture(
    setting: DelayLineRegressionSetting
  ): Future[Unit] = {
    val dsa = dsaf(setting.dataSetId).get

    for {
      // load a ML model
      mlModel <- regressionRepo.get(setting.mlModelId)

      // main item
      item <- dsa.dataSetRepo.get(setting.itemId)

      // replication item
      replicationItem <- setting.replicationItemId.map { replicationId =>
        dsa.dataSetRepo.get(replicationId)
      }.getOrElse(
        Future(None)
      )

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val results = mlService.regressSeriesWithDelayLine(
          item.get, setting.inputSeriesFieldPaths, setting.outputSeriesFieldPath, setting.dlSize, setting.predictAhead, mlModel, setting.learningSetting, replicationItem
        )
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.map { resultsHolder =>
        // prepare the results stats
        val metricStatsMap = MachineLearningUtil.calcMetricStats(resultsHolder.performanceResults)

        val (trainingScore, testingScore, _) = metricStatsMap.get(RegressionEvalMetric.rmse).get

        println("Training RMSE: " + trainingScore)
        println("Test RMSE    : " + testingScore)

        resultsHolder.expectedAndActualOutputs.headOption.map { outputs =>
          val trainingOutputs = outputs.head
          val testOutputs = outputs.tail.head

          exportOutputs(trainingOutputs, "DL_IO_training.svg")
          exportOutputs(testOutputs, "DL_IO_test.svg")
        }
      }
  }

  private def exportOutputs(
    outputs: Seq[(Double, Double)],
    fileName: String
  ) = {
    val y = outputs.map{ case (y, yhat) => y }
    val yhat = outputs.map{ case (y, yhat) => yhat }

    val output = plotter.plotSeries(
      Seq(y, yhat),
      new SeriesPlotSetting()
        .setXLabel("Time")
        .setYLabel("Value")
        .setCaptions(Seq("y", "y^"))
    )

    writeStringAsStream(output, new java.io.File(fileName))
  }

  override def inputType = typeOf[DelayLineRegressionSetting]
}

case class DelayLineRegressionSetting(
  dataSetId: String,
  mlModelId: BSONObjectID,
  itemId: BSONObjectID,
  outputSeriesFieldPath: String,
  inputSeriesFieldPaths: Seq[String],
  dlSize: Int,
  predictAhead: Int,
  featuresNormalizationType: Option[VectorTransformType.Value],
  pcaDims: Option[Int],
  trainingTestingSplit: Option[Double],
  replicationItemId: Option[BSONObjectID],
  repetitions: Option[Int],
  crossValidationFolds: Option[Int],
  crossValidationEvalMetric: Option[RegressionEvalMetric.Value]
) {
  def learningSetting =
    LearningSetting[RegressionEvalMetric.Value](featuresNormalizationType, pcaDims, trainingTestingSplit, Nil, repetitions, crossValidationFolds, crossValidationEvalMetric)
}


