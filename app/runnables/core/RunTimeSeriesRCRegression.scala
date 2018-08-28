package runnables.core

import java.{lang => jl}
import javax.inject.Inject

import com.banda.core.plotter.{Plotter, SeriesPlotSetting}
import com.banda.math.domain.rand.RandomDistribution
import com.banda.network.domain.{ActivationFunctionType, ReservoirSetting}
import models.ml.{IOJsonTimeSeriesSpec, LearningSetting, RegressionEvalMetric, VectorTransformType}
import persistence.RepoTypes.RegressionRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import reactivemongo.bson.BSONObjectID
import runnables.InputFutureRunnable
import services.ml.{MachineLearningService, MachineLearningUtil}
import util.writeStringAsStream

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.Future

class RunTimeSeriesRCRegression @Inject() (
    dsaf: DataSetAccessorFactory,
    mlService: MachineLearningService,
    regressionRepo: RegressionRepo
  ) extends InputFutureRunnable[RunTimeSeriesRCRegressionSpec] {

  private val plotter = Plotter("svg")

  override def runAsFuture(
    setting: RunTimeSeriesRCRegressionSpec
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
        val results = mlService.regressTimeSeries(
          item.get,
          setting.ioSpec,
          setting.predictAhead,
          setting.windowSize,
          Some(setting.reservoirSetting),
          mlModel,
          setting.learningSetting,
          replicationItem
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

  override def inputType = typeOf[RunTimeSeriesRCRegressionSpec]
}

case class RunTimeSeriesRCRegressionSpec(
  dataSetId: String,
  itemId: BSONObjectID,
  ioSpec: IOJsonTimeSeriesSpec,
  mlModelId: BSONObjectID,
  predictAhead: Int,
  windowSize: Option[Int],
  reservoirNodeNum: Int,
  reservoirInDegree: Option[Int],
  reservoirEdgesNum: Option[Int] = None,
  reservoirPreferentialAttachment: Boolean = false,
  reservoirBias: Boolean,
  reservoirCircularInEdges: Option[Seq[Int]] = None,
  inputReservoirConnectivity: Double,
  reservoirSpectralRadius: Option[Double] = None,
  reservoirFunctionType: ActivationFunctionType,
  reservoirFunctionParams: Seq[Double] = Nil,
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

  def reservoirSetting =
    ReservoirSetting(
      inputNodeNum = pcaDims.getOrElse(ioSpec.inputSeriesFieldPaths.size) * windowSize.getOrElse(1),
      bias = 1,
      nonBiasInitial = 0,
      reservoirNodeNum = reservoirNodeNum,
      reservoirInDegree = reservoirInDegree,
      reservoirEdgesNum = reservoirEdgesNum,
      reservoirPreferentialAttachment = reservoirPreferentialAttachment,
      reservoirBias = reservoirBias,
      reservoirCircularInEdges = reservoirCircularInEdges,
      inputReservoirConnectivity = inputReservoirConnectivity,
      reservoirSpectralRadius = reservoirSpectralRadius,
      reservoirFunctionType = reservoirFunctionType,
      reservoirFunctionParams = reservoirFunctionParams,
      weightDistribution = RandomDistribution.createNormalDistribution(classOf[jl.Double], 0d, 1d)
    )
}