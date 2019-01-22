package runnables.core

import javax.inject.Inject

import models.ml.IOJsonTimeSeriesSpec
import persistence.RepoTypes.RegressionRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import reactivemongo.bson.BSONObjectID
import org.incal.core.InputFutureRunnable
import org.incal.spark_ml.models.VectorScalerType
import services.ml.MachineLearningService
import org.incal.spark_ml.models.LearningSetting
import org.incal.spark_ml.models.regression.RegressionEvalMetric

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.Future

class RunTimeSeriesDLRegression @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    regressionRepo: RegressionRepo
  ) extends InputFutureRunnable[RunTimeSeriesDLRegressionSpec] with TimeSeriesResultsHelper {

  override def runAsFuture(
    input: RunTimeSeriesDLRegressionSpec
  ): Future[Unit] = {
    val dsa = dsaf(input.dataSetId).get

    for {
      // load a ML model
      mlModel <- regressionRepo.get(input.mlModelId)

      // main item
      item <- dsa.dataSetRepo.get(input.itemId)

      // replication item
      replicationItem <- input.replicationItemId.map { replicationId =>
        dsa.dataSetRepo.get(replicationId)
      }.getOrElse(
        Future(None)
      )

      // run the selected classifier (ML model)
      resultsHolder <- mlModel.map { mlModel =>
        val results = mlService.regressTemporalSeries(
          item.get,
          input.ioSpec,
          input.predictAhead,
          Some(input.windowSize),
          None,
          mlModel,
          input.learningSetting,
          input.outputNormalizationType,
          input.crossValidationMinTrainingSizeRatio,
          input.trainingTestSplitOrderValue,
          replicationItem
        )
        results.map(Some(_))
      }.getOrElse(
        Future(None)
      )
    } yield
      resultsHolder.foreach(exportResults)
  }

  override def inputType = typeOf[RunTimeSeriesDLRegressionSpec]
}

case class RunTimeSeriesDLRegressionSpec(
  dataSetId: String,
  itemId: BSONObjectID,
  ioSpec: IOJsonTimeSeriesSpec,
  mlModelId: BSONObjectID,
  predictAhead: Int,
  windowSize: Int,
  featuresNormalizationType: Option[VectorScalerType.Value],
  outputNormalizationType: Option[VectorScalerType.Value],
  pcaDims: Option[Int],
  trainingTestSplitRatio: Option[Double],
  trainingTestSplitOrderValue: Option[Double],
  replicationItemId: Option[BSONObjectID],
  repetitions: Option[Int],
  crossValidationFolds: Option[Int],
  crossValidationMinTrainingSizeRatio: Option[Double],
  crossValidationEvalMetric: Option[RegressionEvalMetric.Value]
) {
  def learningSetting =
    LearningSetting[RegressionEvalMetric.Value](featuresNormalizationType, pcaDims, trainingTestSplitRatio, Nil, repetitions, crossValidationFolds, crossValidationEvalMetric)
}


