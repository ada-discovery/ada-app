package runnables.core

import javax.inject.Inject

import com.banda.core.plotter.{Plotter, SeriesPlotSetting}
import com.banda.network.domain.ReservoirSetting
import models.ml.{IOJsonTimeSeriesSpec, LearningSetting, RegressionEvalMetric, VectorTransformType}
import persistence.RepoTypes.RegressionRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import reactivemongo.bson.BSONObjectID
import org.incal.core.InputFutureRunnable
import services.ml.{MachineLearningService, MachineLearningUtil}
import util.writeStringAsStream

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
        val results = mlService.regressTimeSeries(
          item.get,
          input.ioSpec,
          input.predictAhead,
          Some(input.windowSize),
          None,
          mlModel,
          input.learningSetting,
          input.crossValidationMinTrainingSize,
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
  featuresNormalizationType: Option[VectorTransformType.Value],
  pcaDims: Option[Int],
  trainingTestingSplit: Option[Double],
  replicationItemId: Option[BSONObjectID],
  repetitions: Option[Int],
  crossValidationFolds: Option[Int],
  crossValidationMinTrainingSize: Option[Double],
  crossValidationEvalMetric: Option[RegressionEvalMetric.Value]
) {
  def learningSetting =
    LearningSetting[RegressionEvalMetric.Value](featuresNormalizationType, pcaDims, trainingTestingSplit, Nil, repetitions, crossValidationFolds, crossValidationEvalMetric)
}


