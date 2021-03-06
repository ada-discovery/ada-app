package runnables.mpower

import java.{lang => jl, util => ju}

import javax.inject.Inject
import com.bnd.math.business.rand.RandomDistributionProviderFactory
import com.bnd.math.domain.rand.{RandomDistribution, RepeatedDistribution}
import com.bnd.network.business.TopologyFactory
import com.bnd.network.business.learning.ReservoirTrainerFactory
import com.bnd.network.domain.ActivationFunctionType
import org.incal.core.dataaccess.Criterion.Infix
import org.ada.server.models.{ExtendedReservoirLearningSetting, RCPredictionInputOutputSpec}
import org.incal.core.runnables.FutureRunnable
import org.incal.play.GuiceRunnableApp
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.{PlotSetting, PlotlyPlotter}
import play.api.libs.json.JsObject
import services.ml.{RCPredictionResults, RCPredictionService}
import org.incal.core.util.writeStringAsStream

import scala.concurrent.ExecutionContext.Implicits.global

class MPowerPredictAcceleration @Inject() (
    mPowerWalkingRCPredictionService: RCPredictionService,
    reservoirTrainerFactory: ReservoirTrainerFactory,
    topologyFactory: TopologyFactory,
    dsaf: DataSetAccessorFactory
  ) extends FutureRunnable {

//  private val dataSetId = "mpower_challenge.walking_activity_training"
//  private val fieldName = "accel_walking_outboundu002ejsonu002eitems"
//  private val recordId = "020c57df-a99d-4724-a3ce-d426deea9f94"

  private val dataSetId = "lux_park.mpower_walking_activity"
  private val fieldName = "accel_walking_outboundu002ejsonu002eitems"
//  private val fieldName = "deviceMotion_walking_outboundu002ejsonu002eitems.gravity"
  private val recordId = "602681c6-fb35-4513-be00-4992ad00c215"

  private val inputDim = 3
  private val outputDim = 1
  private val inScale = 1
  private val preprocessingType = None // Some(VectorScalerType.StandardScaler)
  private val _predictAhead = 1
  private val washoutPeriod = 500
  private val weightAdaptationIterationNum = 100

  private val ioSpec = RCPredictionInputOutputSpec(
    inputSeriesFieldPaths = Seq(fieldName + ".x", fieldName + ".y", fieldName + ".z"),
    outputSeriesFieldPaths = Seq(fieldName + ".y"),
    dropRightLength = Some(200),
    sourceDataSetId = dataSetId,
    resultDataSetId = "",
    resultDataSetName = ""
  )

  override def runAsFuture = {
    val setting = createReservoirSetting(75, 75, 0.5, 0.8)
    val topology = reservoirTrainerFactory.createThreeLayerReservoirTopology(
      inputDim,
      outputDim,
      setting.getReservoirNodeNum,
      setting.getReservoirInDegree,
      setting.getReservoirInDegreeDistribution,
      setting.getReservoirEdgesNum,
      setting.getReservoirBias,
      setting.getReservoirCircularInEdges,
      setting.getInputReservoirConnectivity,
      setting.getReservoirPreferentialAttachment,
      true,
      false
    )

    val initializedTopology = topologyFactory(topology)

    def resultsFuture(json: JsObject) = mPowerWalkingRCPredictionService.predictSeries(initializedTopology, setting, json, ioSpec)

    for {
      // data set accessor
      dsa <- dsaf.getOrError(dataSetId)

      // retrieve jsons for a given record id
      jsons <- dsa.dataSetRepo.find(
        criteria = Seq("recordId" #== recordId),
        projection = Seq(fieldName),
        limit = Some(1)
      )

      // train / get results
      results1 <- resultsFuture(jsons.head)

      results2 <- resultsFuture(jsons.head)

      results3 <- resultsFuture(jsons.head)
    } yield {
      // plot results
      plotResults(results1.get, "mPowerWalking-prediction1.svg")
      plotResults(results2.get, "mPowerWalking-prediction2.svg")
      plotResults(results3.get, "mPowerWalking-prediction3.svg")
      checkResults(Seq(results1.get, results2.get, results3.get))
    }
  }

  private def plotResults(results: RCPredictionResults, fileName: String) =
    PlotlyPlotter.plotLines(
      data = Seq(
        results.outputs.takeRight(weightAdaptationIterationNum).map(_.doubleValue()),
        results.desiredOutputs.takeRight(weightAdaptationIterationNum).map(_.doubleValue())
      ),
      setting = PlotSetting(
        title = Some("Output vs Desired Output"),
        captions = Seq("Predicted", "Acceleration (x)")
      ),
      outputFileName = fileName
    )

  private def checkResults(results: Traversable[RCPredictionResults]) = {
    def checkResultsAux(name: String, fun: RCPredictionResults => Seq[Double]): Unit =
      results.map(fun).toSeq.transpose.zipWithIndex.foreach { case (nums, index) =>
        val element = nums.head
        if (!nums.forall(x => (Math.abs(x - element) / x) < 0.000001))
          throw new IllegalArgumentException(s"The $name expected to be the same but at $index the values differ: ${nums.mkString(", ")}")
      }

    checkResultsAux("square errors", _.squareErrors)
    checkResultsAux("samp errors", _.sampErrors)
    checkResultsAux("desired outputs", _.desiredOutputs.map(_.doubleValue))
    checkResultsAux("final weights", _.finalWeights.map(_.doubleValue))
    checkResultsAux("outputs", _.outputs.map(_.doubleValue))
  }

  private val weightRdp = RandomDistributionProviderFactory(RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d))
  private val weightRd = new RepeatedDistribution(weightRdp.nextList(5000).toArray[jl.Double](Array[jl.Double]()))

  private def createReservoirSetting(
    reservoirNodeNum: Int,
    reservoirInDegree: Int,
    inputReservoirConnectivity: Double,
    reservoirSpectralRadius: Double
  ) = new ExtendedReservoirLearningSetting {
    setWeightAdaptationIterationNum(weightAdaptationIterationNum)
    setSingleIterationLength(1d)
    setInitialDelay(0d)
    setInputTimeLength(1d)
    setOutputInterpretationRelativeTime(1d)
    setInScale(inScale)
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
//    setWeightDistribution(RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d))
    setWeightDistribution(weightRd)
    setWashoutPeriod(washoutPeriod)
    predictAhead = _predictAhead
    seriesPreprocessingType = preprocessingType
  }
}

object MPowerPredictAcceleration extends GuiceRunnableApp[MPowerPredictAcceleration]