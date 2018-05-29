package runnables.core

import javax.inject.Inject

import models.ml.{LearningSetting, RegressionEvalMetric, VectorTransformType}
import persistence.RepoTypes.RegressionRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import reactivemongo.bson.BSONObjectID
import runnables.InputFutureRunnable
import services.ml.{MachineLearningService, MachineLearningUtil}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.Future

class RunDelayLineSeriesRegression @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    regressionRepo: RegressionRepo
  ) extends InputFutureRunnable[DelayLineRegressionSetting] {

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
        val results = mlService.regressSeries(
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

        val (trainingScore, testingScore, replicationScore) = metricStatsMap.get(RegressionEvalMetric.rmse).get

        println(trainingScore)
        println(testingScore)
      }
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


