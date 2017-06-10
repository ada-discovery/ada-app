package runnables

import java.util.Collections
import java.{lang => jl, util => ju}
import javax.inject.{Inject, Named}

import com.banda.core.plotter.{Plotter, TimeSeriesPlotSetting}
import com.banda.core.util.{DateTimeUtil, FileUtil, RandomUtil}
import com.banda.incal.domain.{CoinDeskPrice, ReservoirLearningSetting}
import com.banda.incal.prediction.{ErrorMeasures, ReservoirTrainerFactory}
import com.banda.math.business.MathUtil
import com.banda.math.business.learning._
import com.banda.math.business.rand.RandomDistributionProviderFactory
import com.banda.math.domain.learning._
import com.banda.math.domain.rand._
import com.banda.network.business._
import com.banda.network.business.learning.NetworkTrainer
import com.banda.network.domain.TemplateTopology.IntraLayerEdgeSpec
import com.banda.network.domain._
import dataaccess.AscSort
import org.junit.Test
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.{JsArray, JsObject, Json}
import dataaccess.Criterion.Infix
import models.{Field, FieldTypeId, FieldTypeSpec}
import services.DataSetService
import services.ml.MachineLearningService
import util.seqFutures

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, _}
import scala.io.Source

class MPowerWalkingRCPrediction @Inject()(
    doubleNetworkRunnableFactory : NetworkRunnableFactory[jl.Double],
    reservoirTrainerFactory: ReservoirTrainerFactory,
    topologyFactory: TopologyFactory,
    mlService: MachineLearningService,
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends Runnable {

  private val dataSetId = "lux_park.mpower_walking_activity"
  private val timeSeriesFieldName = "accel_walking_outboundu002ejsonu002eitems"
  private val otherFieldNames = Seq("recordId", "dataGroups")

  private val resultDataSetId = "lux_park.mpower_walking_activity_rc_weights"
  private val resultDataSetName = "mPower Walking Activity (RC) Weights"

  private val plotter = Plotter.createExportInstance("svg")
  private val fileUtil = FileUtil.getInstance
  private val dsa = dsaf(dataSetId).get
  private val dataSetRepo = dsa.dataSetRepo

  private val weightRdp = RandomDistributionProviderFactory(RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d))
  private val washoutPeriod = 500
  private val predictAhead = 1

  private def createReservoirSetting = new ReservoirLearningSetting {
    setWeightAdaptationIterationNum(2)
    setSingleIterationLength(1d)
    setInitialDelay(0d)
    setInputTimeLength(1d)
    setOutputInterpretationRelativeTime(1d)
    setInScale(1d)
    setOutScale(1d)
    setBias(1d)
    setNonBiasInitial(0d)
    setReservoirNodeNum(50)
    setReservoirInDegree(Some(50))
    setReservoirInDegreeDistribution(None) // Some(RandomDistribution.createPositiveNormalDistribution(classOf[Integer], 50d, 0d))
    setReservoirEdgesNum(None) // Some((0.02 * (250 * 250)).toInt)
    setReservoirPreferentialAttachment(false)
    setReservoirBias(false)
    setInputReservoirConnectivity(0.5d)
    setReservoirSpectralRadius(1)
    setReservoirFunctionType(ActivationFunctionType.Tanh)
    setReservoirFunctionParams(None) // Some(Seq(0.5d : jl.Double, 0.25 * math.Pi : jl.Double, 0d : jl.Double))
    setWeightDistribution(new RepeatedDistribution(weightRdp.nextList(1000).toArray(Array[jl.Double]())))
  }

  override def run = {
    val inputDim = 3
    val outputDim = 1

    val setting = createReservoirSetting
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

    val batchSize = 20
    val future = for {
      count <- dataSetRepo.count()

      // predict
      resultVarianceAndJsons <-
        seqFutures(0 to count / batchSize) {
          groupIndex: Int =>
            dataSetRepo.find(
              limit = Some(batchSize),
              skip = Some(groupIndex * batchSize),
              sort = Seq(AscSort("_id")),
              projection = otherFieldNames ++ Seq(timeSeriesFieldName)
            ).flatMap { jsons =>
              println(s"Processing time series ${groupIndex * batchSize} to ${jsons.size + groupIndex * batchSize}")
              Future.sequence(
                jsons.map { json =>
                  // TODO: here we pass a new instance of reservoir setting because the iteration num is set inside
                  predictSeries(initializedTopology, reservoirNodes, outputNodes, createReservoirSetting)(json).map { case (results, variance) =>
                    val otherDataJson = json.-(timeSeriesFieldName)
                    (results, variance, otherDataJson)
                  }
                }
              )
            }
        }
    } yield {
      val allResults = resultVarianceAndJsons.flatten
      println(allResults.size)
      reportResults(setting, allResults)
    }

    Await.result(future, 10 minutes)
  }

  def predictSeries(
    topology: Topology,
    reservoirNodes: Seq[TopologicalNode],
    outputNodes: Seq[TopologicalNode],
    setting: ReservoirLearningSetting)(
    json: JsObject
  ): Future[(RCPredictionResults, Double)] = {
    val xyzSeries = (json \ timeSeriesFieldName).as[JsArray].value.map { jsValue =>
      val jsObject = jsValue.as[JsObject]
      Seq((jsObject \ "x").as[Double]: jl.Double, (jsObject \ "y").as[Double]: jl.Double, (jsObject \ "z").as[Double]: jl.Double)
    }

    if (xyzSeries.isEmpty) {
      throw new IllegalArgumentException("Series cannot be empty")
    }
    val targetSeries = xyzSeries.map(_ (1))
    val targetVariance = MathUtil.calcStats(0, targetSeries).getVariance.toDouble

    mlService.predictRCTimeSeries(
      setting, topology, reservoirNodes, outputNodes, washoutPeriod, predictAhead, xyzSeries, targetSeries
    ).map(result => (result, targetVariance))
  }

//    val targetVariance = MathUtil.calcStats(0, targetSeries).getVariance
//    reportResults(results, repetitions, targetVariance)

    //    val inputs = (1 to repetitions).map(_ => createIOStream)
    //    val squareSampAndUpDownErrors = computationalGrid.runOnGridSync(callable, inputs).toSeq

//    plotter.plotSeries(
//      Seq(results.head.outputs.takeRight(lastNum): Seq[jl.Double], results.head.desiredOutputs.takeRight(lastNum): Seq[jl.Double]),
//      new TimeSeriesPlotSetting {
//        captions = Seq("Predicted", "Acceleration (x)")
//        title = "Output vs Desired Output"
//      }
//    )
//
//    FileUtil.getInstance().overwriteStringToFileSafe(plotter.getOutput, "mPowerWalking-prediction.svg")
//
//    plotter.plotSeries(
//      Seq(targetSeries),
//      new TimeSeriesPlotSetting {
//        captions = Seq("Acceleration (x)")
//        title = "Acceleration (x)"
//      }
//    )
//
//    FileUtil.getInstance().overwriteStringToFileSafe(plotter.getOutput, "mPowerWalking.svg")

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

  private def saveWeights(results: Traversable[RCPredictionResults]) = {
    val weightsCount = results.head.finalWeights.size
    (0 to weightsCount).map( index =>
      ("W_" + index, FieldTypeSpec(FieldTypeId.Double))
    )
    for {
      metaInfo <- dsa.metaInfo

      newDsa <- dsaf.register(
        metaInfo.copy(_id = None, id = resultDataSetId, name = resultDataSetName, timeCreated = new ju.Date()), None, None
      )

//      _ <- dataSetService.

    } yield
      ()
  }

  private def reportResults(
    setting: ReservoirLearningSetting,
    resultVarianceAndJsons: Traversable[(RCPredictionResults, Double, JsObject)]
  ) = {
    val lastNum = setting.getWeightAdaptationIterationNum

    val results = resultVarianceAndJsons.map(_._1)

    val meanSamps = results.map(_.sampErrors.takeRight(lastNum)).transpose.map(s => s.sum / s.size)

    val lastRnmses = resultVarianceAndJsons.map { case (result, variance, _) =>
      val meanSquare = result.squareErrors.takeRight(lastNum).sum / lastNum
      math.sqrt(meanSquare / variance)
    }

    val meanRnmseLast = lastRnmses.sum / lastRnmses.size
    val meanSampLast = meanSamps.sum / lastNum

    println("In scale                      : " + setting.getInScale)
    println("Reservoir node #              : " + setting.getReservoirNodeNum)

    if (setting.getReservoirInDegree.isDefined)
      println("Reservoir in-degree           : " + setting.getReservoirInDegree.get)

    if (setting.getReservoirPreferentialAttachment)
      println("Reservoir pref attachment     : " + setting.getReservoirPreferentialAttachment)

    println("Reservoir bias                : " + setting.getReservoirBias)

    if (setting.getReservoirInDegreeDistribution.isDefined) {
      val distribution = setting.getReservoirInDegreeDistribution.get.asInstanceOf[ShapeLocationDistribution[jl.Double]]
      println("Reservoir in-degree dist    : " + distribution.getLocation + ", " + distribution.getShape)
    }

    if (setting.getReservoirEdgesNum.isDefined)
      println("Reservoir edges num           : " + setting.getReservoirEdgesNum.get)

    println("Input-Reservoir Connect       : " + setting.getInputReservoirConnectivity)

    if (setting.getWeightDistribution.isInstanceOf[ShapeLocationDistribution[jl.Double]])
      println("Weight Distribution STD       : " + setting.getWeightDistribution.asInstanceOf[ShapeLocationDistribution[jl.Double]].getShape)

    println("Spectral radius               : " + setting.getReservoirSpectralRadius)
    println("Reservoir function            : " + setting.getReservoirFunctionType)
    println("---------------------------------------------")
    println("Mean SAMP                     : " + meanSampLast)
    println("Mean RNMSE                    : " + meanRnmseLast)
  }
}

case class RCPredictionResults(
  squareErrors: Seq[Double],
  sampErrors: Seq[Double],
  outputs: Seq[jl.Double],
  desiredOutputs: Seq[jl.Double],
  finalWeights: Seq[jl.Double]
)

object MPowerWalkingRCPrediction extends GuiceBuilderRunnable[MPowerWalkingRCPrediction] with App { run }