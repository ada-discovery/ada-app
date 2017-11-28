package runnables

import java.{lang => jl, util => ju}
import javax.inject.Inject

import com.banda.core.plotter.{Plotter, TimeSeriesPlotSetting}
import com.banda.core.util.FileUtil
import com.banda.incal.prediction.ReservoirTrainerFactory
import com.banda.math.business.rand.RandomDistributionProviderFactory
import com.banda.math.domain.rand.{RandomDistribution, RepeatedDistribution}
import com.banda.network.business.TopologyFactory
import com.banda.network.domain._
import dataaccess.Criterion.Infix
import models.ml.{ExtendedReservoirLearningSetting, RCPredictionInputOutputSpec, VectorTransformType}
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import services.ml.{RCPredictionResults, RCPredictionService}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global

class PoloniexPrediction @Inject()(
    mPowerWalkingRCPredictionService: RCPredictionService,
    reservoirTrainerFactory: ReservoirTrainerFactory,
    topologyFactory: TopologyFactory,
    dsaf: DataSetAccessorFactory
  ) extends FutureRunnable {

  private val fieldName = "series"
  private val id = BSONObjectID.parse("599897a2c500009402a379fd").get // BSONObjectID("599897a3c500009402a379fe")
  private val ioSpec = RCPredictionInputOutputSpec(
    inputSeriesFieldPaths = Seq("weightedAverage", "high", "low", "volume").map(fieldName + "." + _),
    outputSeriesFieldPaths = Seq("weightedAverage").map(fieldName + "." + _),
    sourceDataSetId = "btc.poloniex",
    resultDataSetId = "",
    resultDataSetName = ""
  )

  private val dsa = dsaf(ioSpec.sourceDataSetId).get

  private val inScale = 1
  private val preprocessingType = Some(VectorTransformType.StandardScaler)
  private val _predictAhead = 1
  private val washoutPeriod = 20
  private val adaptationLength = 100
  private val repetitions = 40

  private val plotter = Plotter.createExportInstance("svg")

  override def runAsFuture = {
    val setting = createReservoirSetting(200, 1, 0.95, None, Some(Seq(0,1)))
    val topology = reservoirTrainerFactory.createThreeLayerReservoirTopology(
      ioSpec.inputSeriesFieldPaths.size,
      ioSpec.outputSeriesFieldPaths.size,
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

    def resultFuture(topology: Topology, json: JsObject) = mPowerWalkingRCPredictionService.predictSeries(topology, setting, json, ioSpec)

    def calcMatches(result: RCPredictionResults, fromLastNum: Int, length: Int) = {
      def takeLast[T](seq: Seq[T]) = seq.takeRight(fromLastNum).take(length)

      val matches = takeLast(result.desiredOutputs).zip(takeLast(result.outputs)).count { case (a, b) =>
        (a > 0 && b > 0) || (a < 0 && b < 0)
      }
      matches.toDouble / length
    }

    for {
      // retrieve the json for a given record id
      json <- dsa.dataSetRepo.get(id)

      // train / get results
      results <- util.seqFutures(1 to repetitions) { _ =>
        val initializedTopology = topologyFactory(topology)
        resultFuture(initializedTopology, json.get).map { case Some(result) =>
          (result, initializedTopology)
        }
      }
    } yield {
      val topologyOutputUpDownMatches = results.map { case (result, topology) =>
        (topology, calcMatches(result, adaptationLength / 2, adaptationLength / 2))
      }
      val outputUpDownMatches = topologyOutputUpDownMatches.map(_._2)

      val topologyTestingUpDownMatches = results.map { case (result, topology) =>
        (topology, calcMatches(result, adaptationLength, adaptationLength / 2))
      }

      val bestTestingTopology = topologyTestingUpDownMatches.maxBy(_._2)._1
      val bestCrossValidatedUpDownMatches = topologyOutputUpDownMatches.find(_._1 == bestTestingTopology).get._2

      val meanUpDownMatches = outputUpDownMatches.sum / results.size
      println("Mean Up-down matches           : " + meanUpDownMatches)
      println("Max Up-down matches            : " + outputUpDownMatches.max)
      println("Min Up-down matches            : " + outputUpDownMatches.min)
      println("Cross-validated Up-Down matches: " + bestCrossValidatedUpDownMatches)

      println("---------------")
      outputUpDownMatches.foreach(println(_))
      // plot cross-validated results
      val bestCrossValidatedResults = results.find(_._2 == bestTestingTopology).get._1
      plotResults(bestCrossValidatedResults, "Poloniex-prediction1.svg")
    }
  }

  private def plotResults(results: RCPredictionResults, fileName: String) = {
    plotter.plotSeries(
      Seq(results.outputs.takeRight(adaptationLength): Seq[jl.Double], results.desiredOutputs.takeRight(adaptationLength): Seq[jl.Double]),
          new TimeSeriesPlotSetting {
            captions = Seq("Predicted", "Actual (x)")
            title = "Output vs Desired Output"
          }
        )

    FileUtil.getInstance().overwriteStringToFileSafe(plotter.getOutput, fileName)
  }

  private val weightRdp = RandomDistributionProviderFactory(RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d))
  private val weightRd = new RepeatedDistribution(weightRdp.nextList(5000).toArray[jl.Double](Array[jl.Double]()))

  private def createReservoirSetting(
    reservoirNodeNum: Int,
    inputReservoirConnectivity: Double,
    reservoirSpectralRadius: Double,
    reservoirInDegree: Option[Int] = None,
    reservoirCircularInEdges: Option[Seq[Int]] = None
  ) = new ExtendedReservoirLearningSetting {
    setWeightAdaptationIterationNum(adaptationLength)
    setSingleIterationLength(1d)
    setInitialDelay(0d)
    setInputTimeLength(1d)
    setOutputInterpretationRelativeTime(1d)
    setInScale(inScale)
    setOutScale(1d)
    setBias(1d)
    setNonBiasInitial(0d)
    setReservoirNodeNum(reservoirNodeNum)
    setReservoirInDegree(reservoirInDegree)
    setReservoirInDegreeDistribution(None) // Some(RandomDistribution.createPositiveNormalDistribution(classOf[Integer], 50d, 0d))
    setReservoirEdgesNum(None) // Some((0.02 * (250 * 250)).toInt)
    setReservoirPreferentialAttachment(false)
    setReservoirBias(false)
    setReservoirCircularInEdges(reservoirCircularInEdges)
    setInputReservoirConnectivity(inputReservoirConnectivity)
    setReservoirSpectralRadius(reservoirSpectralRadius)
    setReservoirFunctionType(ActivationFunctionType.Sinus)
    setReservoirFunctionParams(Some(Seq(0.5d : jl.Double, 0.25 * math.Pi : jl.Double, 0d : jl.Double)))
//    setWeightDistribution(RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d))
    setWeightDistribution(weightRd)
    setWashoutPeriod(washoutPeriod)
    predictAhead = _predictAhead
    seriesPreprocessingType = preprocessingType
  }

  def createThreeLayerReservoirCircularTopology(
    inputNum: Int,
    outputNum: Int,
    reservoirNum: Int,
    inputReservoirConnectivity: Double
  ) = {
    // layer 1
    val inputLayer = new TemplateTopology
    inputLayer.setIndex(1)
    inputLayer.setNodesNum(inputNum)

    // layer 2
    val reservoir = new SpatialTopology
    reservoir.setIndex(2)
    reservoir.addSize(reservoirNum: jl.Integer)

    val neighborhood = new SpatialNeighborhood

    val neighbor = new SpatialNeighbor
    neighbor.addCoordinateDiff(1: jl.Integer)
    neighborhood.addNeighbor(neighbor)

    val neighbor2 = new SpatialNeighbor
    neighbor2.addCoordinateDiff(2: jl.Integer)
    neighborhood.addNeighbor(neighbor2)

    val neighbor3 = new SpatialNeighbor
    neighbor3.addCoordinateDiff(3: jl.Integer)
    neighborhood.addNeighbor(neighbor3)

    val neighbor4 = new SpatialNeighbor
    neighbor4.addCoordinateDiff(5: jl.Integer)
    neighborhood.addNeighbor(neighbor4)

    reservoir.setNeighborhood(neighborhood)
    reservoir.setTorusFlag(true)

//    val reservoir = new TemplateTopology
//    reservoir.setIndex(2)
//    reservoir.setNodesNum(reservoirNum)
//    reservoir.setInEdgesNum(reservoirNum)
//    reservoir.setAllowMultiEdges(false)
//    reservoir.setAllowSelfEdges(true)

    // layer 3
    val readout = new TemplateTopology
    readout.setIndex(3)
    readout.setNodesNum(outputNum)
    readout.setGenerateBias(true)

    // top-level topology
    val topLevelTopology = new TemplateTopology
    topLevelTopology.addLayer(inputLayer)
    topLevelTopology.addLayer(reservoir)
    topLevelTopology.addLayer(readout)

    //    topLevelTopology.setIntraLayerAllEdges(true)

    val inputReservoirEdgeSpec = new TemplateTopology.IntraLayerEdgeSpec()
    inputReservoirEdgeSpec.setAllowMultiEdges(false)
    //    inputReservoirEdgeSpec.setInEdgesNum(1)
    inputReservoirEdgeSpec.setEdgesNum((inputReservoirConnectivity * reservoirNum * inputNum).toInt)

    val reservoirOutputEdgeSpec = new TemplateTopology.IntraLayerEdgeSpec()
    reservoirOutputEdgeSpec.setAllEdges(true)

    topLevelTopology.addIntraLayerEdgeSpec(inputReservoirEdgeSpec)
    topLevelTopology.addIntraLayerEdgeSpec(reservoirOutputEdgeSpec)

    topLevelTopology
  }
}

object PoloniexPrediction extends GuiceBuilderRunnable[PoloniexPrediction] with App { run }