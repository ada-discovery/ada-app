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
import play.api.libs.json.{util => _, _}
import dataaccess.Criterion.Infix
import models.DataSetFormattersAndIds.JsObjectIdentity
import models._
import reactivemongo.bson.BSONObjectID
import runnables.DataSetId.denopa_raw_clinical_baseline
import services.DataSetService
import services.ml.MachineLearningService
import _root_.util.seqFutures
import reactivemongo.play.json.BSONFormats._

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

  private val timeSeriesFieldName = "accel_walking_outboundu002ejsonu002eitems"
//  private val timeSeriesFieldName = "accel_walking_outboundjsonitems"

//  private val dataSetId = "lux_park.mpower_walking_activity"
//  private val resultDataSetId = "lux_park.mpower_walking_activity_rc_weights"
//  private val resultDataSetName = "mPower Walking Activity (RC) Weights"
//  private val otherFieldNames = Seq("recordId", "dataGroups", "healthCode")

  private val dataSetId = "mpower_challenge.walking_activity_training"
  private val resultDataSetId = "mpower_challenge.walking_activity_training_rc_weights"
  private val resultDataSetName = "Walking Activity Training (RC) Weights"
  private val otherFieldNames = Seq("recordId", "medTimepoint", "healthCode")

//  private val dataSetId = "mpower_challenge.walking_activity_testing"
//  private val resultDataSetId = "mpower_challenge.walking_activity_testing_rc_weights"
//  private val resultDataSetName = "Walking Activity Testing (RC) Weights"
//  private val otherFieldNames = Seq("recordId", "healthCode")

  private val demographicsDataSetId = "mpower_challenge.demographics_training"
  private val demographicsDsa = dsaf(demographicsDataSetId).get
  private val professionalDiagnosisFieldNameSpec = ("professional-diagnosis", FieldTypeSpec(FieldTypeId.Boolean))

  private val resultDataSetSetting = DataSetSetting(
    None,
    resultDataSetId,
    "resultId",
    None,
    None,
    None,
    "rc_w_",
    None,
    None,
    false,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.ElasticSearch,
    false
  )

  private val plotter = Plotter.createExportInstance("svg")
  private val fileUtil = FileUtil.getInstance
  private val dsa = dsaf(dataSetId).get
  private val dataSetRepo = dsa.dataSetRepo

  private val weightRdp = RandomDistributionProviderFactory(RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d))
  private val washoutPeriod = 500
  private val dropRightLength = 500
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
    setInputReservoirConnectivity(1d)
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
    val idName = JsObjectIdentity.name
    val future = for {
      count <- dataSetRepo.count()

      ids <- dataSetRepo.find(
        projection = Seq(idName),
        sort = Seq(AscSort(idName))
      ).map(_.map(json => (json \ idName).as[BSONObjectID]))

      // predict
      resultVarianceAndJsons <-
//        seqFutures(0 to Math.ceil(count.toDouble / batchSize).toInt) {
      seqFutures(ids.toSeq.grouped(batchSize).zipWithIndex) {
//          groupIndex: Int =>
        case (ids, groupIndex) =>
            dataSetRepo.find(
//              skip = Some(groupIndex * batchSize),
              criteria = Seq(idName #>= ids.head),
              limit = Some(batchSize),
              sort = Seq(AscSort(idName)),
              projection = otherFieldNames ++ Seq(timeSeriesFieldName)
            ).flatMap { jsons =>
              println(s"Processing time series ${groupIndex * batchSize} to ${jsons.size + (groupIndex * batchSize)}")
              Future.sequence(
                jsons.map { json =>
                  // TODO: here we pass a new instance of reservoir setting because the iteration num is set inside
                  predictSeries(initializedTopology, reservoirNodes, outputNodes, createReservoirSetting)(json).map(
                    _.map { case (results, variance) =>
                        val otherDataJson = json.-(timeSeriesFieldName)
                        (results, variance, otherDataJson)
                    }
                  )
                }
              )
            }
        }

      allResults = resultVarianceAndJsons.flatten.flatten

      fields <- dsa.fieldRepo.find(Seq("name" #-> otherFieldNames))

      _ <- saveWeightDataSet(fields, allResults)
    } yield {
      println(allResults.size)
      reportResults(setting, allResults)
    }

    Await.result(future, 1200 minutes)
  }

  def predictSeries(
    topology: Topology,
    reservoirNodes: Seq[TopologicalNode],
    outputNodes: Seq[TopologicalNode],
    setting: ReservoirLearningSetting)(
    json: JsObject
  ): Future[Option[(RCPredictionResults, Double)]] = {
    val xyzSeries = (json \ timeSeriesFieldName).asOpt[JsArray].map( jsonArray =>
      jsonArray.value.dropRight(dropRightLength).map { jsValue =>
        val jsObject = jsValue.as[JsObject]
        Seq((jsObject \ "x").as[Double]: jl.Double, (jsObject \ "y").as[Double]: jl.Double, (jsObject \ "z").as[Double]: jl.Double)
      }
    )

    if (xyzSeries.isDefined && xyzSeries.get.nonEmpty) {
      val targetSeries = xyzSeries.get.map(_ (1))
      val targetVariance = MathUtil.calcStats(0, targetSeries).getVariance.toDouble

      mlService.predictRCTimeSeries(
        setting, topology, reservoirNodes, outputNodes, washoutPeriod, predictAhead, xyzSeries.get, targetSeries
      ).map(result => Some((result, targetVariance)))
    } else
      Future(None)
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

  private def saveWeightDataSet(
    fields: Traversable[Field],
    resultVarianceAndJsons: Traversable[(RCPredictionResults, Double, JsObject)]
  ): Future[Unit] = {
    val weightsCount = resultVarianceAndJsons.head._1.finalWeights.size

    val weightFieldNameTypeSpecs = (0 to weightsCount).map( index =>
      ("rc_w_" + index, FieldTypeSpec(FieldTypeId.Double))
    )

    for {
      metaInfo <- dsa.metaInfo

      newDsa <- dsaf.register(
        metaInfo.copy(_id = None, id = resultDataSetId, name = resultDataSetName, timeCreated = new ju.Date()),
        Some(resultDataSetSetting),
        None
      )

      healthCodeDiagnosisJsonMap <- demographicsDsa.dataSetRepo.find(
        projection = Seq("healthCode", "professional-diagnosis")
      ).map(_.map{ json =>
        val healthCode = (json \ "healthCode").as[String]
        val diagnosisJson = (json \ "professional-diagnosis").getOrElse(JsNull)
        (healthCode, diagnosisJson)
      }.toMap)

      _ <- {
        val originalFieldTypeSpecs = fields.map( field => (field.name, field.fieldTypeSpec))

        val fieldSpecs = originalFieldTypeSpecs ++ weightFieldNameTypeSpecs ++ Seq(professionalDiagnosisFieldNameSpec)

        dataSetService.updateDictionary(
          resultDataSetId, fieldSpecs, false, true
        )
      }

      _ <- newDsa.dataSetRepo.deleteAll

      _ <- {
        val newJsons = resultVarianceAndJsons.map { case (results, _, json) =>
          val weightJson = JsObject(
            results.finalWeights.zipWithIndex.map { case (weight, index) =>
              ("rc_w_" + index, JsNumber(BigDecimal.valueOf(weight)))
            }
          )
          val jsonWithWeights = json ++ weightJson

          val healthCode = (json \ "healthCode").as[String]
          val diagnosisJson = healthCodeDiagnosisJsonMap.get(healthCode).getOrElse(JsNull)
          jsonWithWeights + ("professional-diagnosis", diagnosisJson)
        }

        dataSetService.saveOrUpdateRecords(newDsa.dataSetRepo, newJsons.toSeq, None, false, None, Some(100))
      }
    } yield
      ()
  }

  private def reportResults(
    setting: ReservoirLearningSetting,
    resultVarianceAndJsons: Traversable[(RCPredictionResults, Double, JsObject)]
  ) = {
    val lastNum = setting.getWeightAdaptationIterationNum

    val results = resultVarianceAndJsons.map(_._1)

    val meanSamps = results.map { x =>
      if (x.sampErrors.size >= lastNum)
        Some(x.sampErrors.takeRight(lastNum))
      else
        None
    }.flatten.transpose.map(s => s.sum / s.size)

    val lastRnmses = resultVarianceAndJsons.map { case (result, variance, _) =>
      if (result.squareErrors.size >= lastNum) {
        val meanSquare = result.squareErrors.takeRight(lastNum).sum / lastNum
        Some(math.sqrt(meanSquare / variance))
      } else
        None
    }.flatten

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