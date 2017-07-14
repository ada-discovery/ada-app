package runnables

import java.util.Collections
import java.{lang => jl, util => ju}
import javax.inject.Inject

import com.banda.core.plotter.{Plotter, TimeSeriesPlotSetting}
import com.banda.core.util.FileUtil
import com.banda.incal.domain.ReservoirLearningSetting
import com.banda.incal.prediction.ReservoirTrainerFactory
import com.banda.math.domain.rand.{RandomDistribution, RepeatedDistribution}
import com.banda.network.business.TopologyFactory
import com.banda.network.domain.ActivationFunctionType
import persistence.dataset.DataSetAccessorFactory
import services.ml.MachineLearningService

import scala.collection.JavaConversions._
import dataaccess.Criterion.Infix
import services.{RCPredictionService, RCPredictionResults}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await

class MPowerPredictAcceleration @Inject() (
    mPowerWalkingRCPredictionService: RCPredictionService,
    reservoirTrainerFactory: ReservoirTrainerFactory,
    topologyFactory: TopologyFactory,
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  private val timeout = 120000 millis

  private val dataSetId = "mpower_challenge.walking_activity_training"
  private val fieldName = "accel_walking_outboundu002ejsonu002eitems"
  private val recordId = "020c57df-a99d-4724-a3ce-d426deea9f94"

//  private val dataSetId = "lux_park.mpower_walking_activity"
//  private val fieldName = "accel_walking_outboundu002ejsonu002eitems"
////  private val fieldName = "deviceMotion_walking_outboundu002ejsonu002eitems.gravity"
//  private val recordId = "602681c6-fb35-4513-be00-4992ad00c215"

  private val dsa = dsaf(dataSetId).get

  private val inputDim = 3
  private val outputDim = 1
  private val washoutPeriod = 500
  private val dropRight = 200
  private val weightAdaptationIterationNum = 100

  private val plotter = Plotter.createExportInstance("svg")
  private val fileUtil = FileUtil.getInstance

  override def run = {
    val setting = createReservoirSetting(75, 75, 0.5, 0.8)
    val topology = reservoirTrainerFactory.createThreeLayerReservoirTopology(
      inputDim,
      outputDim,
      setting.getReservoirNodeNum,
      setting.getReservoirInDegree,
      setting.getReservoirInDegreeDistribution,
      setting.getReservoirEdgesNum,
      setting.getReservoirBias,
      setting.getInputReservoirConnectivity,
      setting.getReservoirPreferentialAttachment,
      true,
      false
    )

    val initializedTopology = topologyFactory(topology)
    val layers = initializedTopology.getLayers.toSeq
    val reservoirNodes = new ju.ArrayList(layers(1).getAllNodes)
    Collections.sort(reservoirNodes)
    val outputNodes = new ju.ArrayList(layers(2).getAllNodes)
    Collections.sort(outputNodes)

    val future = for {
      jsons <- dsa.dataSetRepo.find(
        criteria = Seq("recordId" #== recordId),
        projection = Seq(fieldName),
        limit = Some(1)
      )

      results <- mPowerWalkingRCPredictionService.predictSeries(
        initializedTopology, reservoirNodes, outputNodes, setting, washoutPeriod, dropRight)(
        jsons.head,
        Seq(fieldName + ".x", fieldName + ".y", fieldName + ".z"),
        Seq(fieldName + ".y")
      )

    } yield {
      plotResults(results.get)
    }

    Await.result(future, timeout)
  }

  private def plotResults(results: RCPredictionResults) = {
    plotter.plotSeries(
      Seq(results.outputs.takeRight(weightAdaptationIterationNum): Seq[jl.Double], results.desiredOutputs.takeRight(weightAdaptationIterationNum): Seq[jl.Double]),
          new TimeSeriesPlotSetting {
            captions = Seq("Predicted", "Acceleration (x)")
            title = "Output vs Desired Output"
          }
        )

    FileUtil.getInstance().overwriteStringToFileSafe(plotter.getOutput, "mPowerWalking-prediction.svg")
  }

  private def createReservoirSetting(
    reservoirNodeNum: Int,
    reservoirInDegree: Int,
    inputReservoirConnectivity: Double,
    reservoirSpectralRadius: Double
  ) = new ReservoirLearningSetting {
    setWeightAdaptationIterationNum(weightAdaptationIterationNum)
    setSingleIterationLength(1d)
    setInitialDelay(0d)
    setInputTimeLength(1d)
    setOutputInterpretationRelativeTime(1d)
    setInScale(1d)
    setOutScale(1d)
    setBias(1d)
    setNonBiasInitial(0d)
    setReservoirNodeNum(reservoirNodeNum)
    setReservoirInDegree(Some(reservoirInDegree))
    setReservoirInDegreeDistribution(None) // Some(RandomDistribution.createPositiveNormalDistribution(classOf[Integer], 50d, 0d))
    setReservoirEdgesNum(None) // Some((0.02 * (250 * 250)).toInt)
    setReservoirPreferentialAttachment(false)
    setReservoirBias(false)
    setInputReservoirConnectivity(inputReservoirConnectivity)
    setReservoirSpectralRadius(reservoirSpectralRadius)
    setReservoirFunctionType(ActivationFunctionType.Tanh)
    setReservoirFunctionParams(None) // Some(Seq(0.5d : jl.Double, 0.25 * math.Pi : jl.Double, 0d : jl.Double))
    setWeightDistribution(RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d))
  }
}

object MPowerPredictAcceleration extends GuiceBuilderRunnable[MPowerPredictAcceleration] with App { run }

