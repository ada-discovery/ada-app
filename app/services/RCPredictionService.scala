package services

import java.util.Collections
import java.{lang => jl, util => ju}
import javax.inject.Inject

import _root_.util.{FieldUtil, JsonUtil, seqFutures}
import FieldUtil.caseClassToFlatFieldTypes
import com.banda.core.plotter.Plotter
import com.banda.incal.domain.ReservoirLearningSetting
import com.banda.incal.prediction.ReservoirTrainerFactory
import com.banda.math.domain.rand._
import com.banda.network.business._
import com.banda.network.domain._
import com.google.inject.{ImplementedBy, Singleton}
import dataaccess.AscSort
import dataaccess.Criterion.Infix
import models.DataSetFormattersAndIds.JsObjectIdentity
import models._
import models.ml.{ExtendedReservoirLearningSetting, RCPredictionInputOutputSpec, RCPredictionSetting, RCPredictionSettingAndResults}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json.{util => _, _}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import services.ml.MachineLearningService
import models.ml.RCPredictionSettingAndResults.rcPredictionSettingAndResultsFormat

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[RCPredictionServiceImpl])
trait RCPredictionService {

  type JsonsAndFields = (Seq[JsObject], Traversable[Field])

  def predictAndStoreResults(
    setting: ExtendedReservoirLearningSetting,
    ioSpec: RCPredictionInputOutputSpec,
    batchSize: Option[Int] = None,
    transformWeightResultJsonsAndFields: Option[JsonsAndFields => JsonsAndFields] = None
  ): Future[Unit]

  def predictSeries(
    topology: Topology,
    setting: ExtendedReservoirLearningSetting,
    dropRightLength: Int,
    json: JsObject,
    inputSeriesFieldPaths: Seq[String],
    outputSeriesFieldPaths: Seq[String]
  ): Future[Option[RCPredictionResults]]
}

@Singleton
class RCPredictionServiceImpl @Inject()(
    reservoirTrainerFactory: ReservoirTrainerFactory,
    topologyFactory: TopologyFactory,
    mlService: MachineLearningService,
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends RCPredictionService {

  private val otherFieldNames1 = Seq("recordId", "medTimepoint", "healthCode")
  private val otherFieldNames2 = Seq("recordId")

  private val settingAndResultsFields =
    caseClassToFlatFieldTypes[RCPredictionSettingAndResults]("-").filter(_._1 != "_id")

  private def resultWeightDataSetSetting(resultDataSetId: String) =
    new DataSetSetting(resultDataSetId, StorageType.ElasticSearch, "rc_w_0")

  private def resultDataSetSetting(resultDataSetId: String) =
    new DataSetSetting(resultDataSetId, StorageType.ElasticSearch, "reservoirNodeNum")

  private val defaultBatchSize = 20

  override def predictAndStoreResults(
    setting: ExtendedReservoirLearningSetting,
    ioSpec: RCPredictionInputOutputSpec,
    batchSize: Option[Int] = None,
    transformWeightResultJsonsAndFields: Option[JsonsAndFields => JsonsAndFields] = None
  ): Future[Unit] = {
    val idName = JsObjectIdentity.name
    val dsa = dsaf(ioSpec.sourceDataSetId).get
    val dataSetRepo = dsa.dataSetRepo

    val inputDim = ioSpec.inputSeriesFieldPaths.size
    val outputDim = ioSpec.outputSeriesFieldPaths.size

    val topology = createTopology(setting, inputDim, outputDim)

    val otherFieldNames = if (transformWeightResultJsonsAndFields.isDefined) otherFieldNames1 else otherFieldNames2
    val seriesFieldNames = ioSpec.inputSeriesFieldPaths.map(_.split('.').head) ++ ioSpec.outputSeriesFieldPaths.map(_.split('.').head)

    // helper method to execute prediction on a given json
    def predict(json: JsObject): Future[Option[(RCPredictionResults, JsObject)]] =
      for {
        results <- predictSeries(
          topology, setting, ioSpec.dropRightLength,
          json,
          ioSpec.inputSeriesFieldPaths,
          ioSpec.outputSeriesFieldPaths
        )
      } yield
        results.map { results =>
          val otherDataJson = seriesFieldNames.foldLeft(json) { case (json, fieldName) => json.-(fieldName) }
          (results, otherDataJson)
        }

    val actualBatchSize = batchSize.getOrElse(defaultBatchSize)

    for {
      // get the data set meta info
      metaInfo <- dsa.metaInfo

      // retrieve the total count
      count <- dataSetRepo.count()

      // get all the ids
      ids <- dataSetRepo.find(
        projection = Seq(idName),
        sort = Seq(AscSort(idName))
      ).map(_.map(json => (json \ idName).as[BSONObjectID]))

      // predict the time series in batches and retrieve the results with jsons
      resultsAndJsons <-
        seqFutures(ids.toSeq.grouped(actualBatchSize).zipWithIndex) {

          case (ids, groupIndex) =>
            dataSetRepo.find(
              criteria = Seq(idName #>= ids.head),
              limit = Some(actualBatchSize),
              sort = Seq(AscSort(idName)),
              projection = otherFieldNames ++ seriesFieldNames.toSet
            ).flatMap { jsons =>
              println(s"Processing time series ${groupIndex * actualBatchSize} to ${(jsons.size - 1) + (groupIndex * actualBatchSize)}")
              Future.sequence(jsons.map(predict))
            }
        }

      // flatten the results
      allResults = resultsAndJsons.flatten.flatten

      // get the info about the fields we want to save alongside the trained weights
      fields <- dsa.fieldRepo.find(Seq("name" #-> otherFieldNames))

      // save the weight matrix
      _ <- {
        val (transformedResults, transformedFields) =
          transformWeightResultJsonsAndFields.map { transform =>
            val (newJsons, newFields) = transform(allResults.map(_._2), fields)
            val newResultsWithJsons = allResults.map(_._1).zip(newJsons)
            (newResultsWithJsons, newFields)
          }.getOrElse(
            (allResults, fields)
          )

        saveWeightDataSet(metaInfo, ioSpec.resultDataSetId, ioSpec.resultDataSetName, transformedFields, transformedResults)
      }

      // calc the errors and save the results
      _ <- {
        val (meanRnmse, meanSamp) = calcErrors(
          allResults.map(_._1),
          setting.getWeightAdaptationIterationNum
        )

        println(s"Mean RNMSE $meanRnmse, mean SAMP $meanSamp")

        val settingAndResults = RCPredictionSettingAndResults(
          None,
          RCPredictionSetting(
            setting.getReservoirNodeNum,
            setting.getReservoirInDegree.get,
            setting.getInputReservoirConnectivity,
            setting.getReservoirSpectralRadius,
            setting.getInScale,
            setting.seriesPreprocessingType,
            setting.getWashoutPeriod,
            setting.predictAhead
          ),
          ioSpec,
          meanSamp,
          meanRnmse
        )

        saveResults(metaInfo, settingAndResults, ioSpec.sourceDataSetId + "_results", metaInfo.name + " Results")
      }
    } yield {
      println(allResults.size)
      reportResults(setting, allResults)
    }
  }

  private def createTopology(
    setting: ReservoirLearningSetting,
    inputDim: Int,
    outputDim: Int
  ): Topology = {
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

    topologyFactory(topology)
  }

  override def predictSeries(
    topology: Topology,
    setting: ExtendedReservoirLearningSetting,
    dropRightLength: Int,
    json: JsObject,
    inputSeriesFieldPaths: Seq[String],
    outputSeriesFieldPaths: Seq[String]
  ): Future[Option[RCPredictionResults]] = {
    // helper method to extract series for a given path
    def extractSeries(path: String) = {
      val jsValues = JsonUtil.traverse(json, path)
      jsValues.dropRight(dropRightLength).map(_.as[Double]: jl.Double)
//      series.zip(series.tail).map { case (value, next) => ((next - value) / value): jl.Double }
    }

    val inputSeries = inputSeriesFieldPaths.map(extractSeries).transpose

    // TODO: only the first output field is taken
    val outputSeries = extractSeries(outputSeriesFieldPaths.head)

    if (inputSeries.nonEmpty) {
      mlService.predictRCTimeSeries(
        setting,
        topology,
        inputSeries,
        outputSeries
      ).map(result => Some(result))
    } else
      Future(None)
  }

  private def saveWeightDataSet(
    sourceDataSetMetaInfo: DataSetMetaInfo,
    resultDataSetId: String,
    resultDataSetName: String,
    fields: Traversable[Field],
    resultsAndJsons: Traversable[(RCPredictionResults, JsObject)]
  ): Future[Unit] =
    for {
      // register the weight output data set
      weightDsa <- dsaf.register(
        sourceDataSetMetaInfo.copy(_id = None, id = resultDataSetId, name = resultDataSetName, timeCreated = new ju.Date()),
        Some(
          resultWeightDataSetSetting(resultDataSetId)
        ),
        None
      )

      weightsCount = resultsAndJsons.head._1.finalWeights.size

      // update the dictionary
      _ <- {
        val originalFieldTypeSpecs = fields.map( field => (field.name, field.fieldTypeSpec))
        val weightFieldNameTypeSpecs = (0 until weightsCount).map( index =>
          ("rc_w_" + index, FieldTypeSpec(FieldTypeId.Double))
        )

        val fieldSpecs = originalFieldTypeSpecs ++ weightFieldNameTypeSpecs

        dataSetService.updateDictionary(resultDataSetId, fieldSpecs, false, true)
      }

      // delete all the data
      _ <- weightDsa.dataSetRepo.deleteAll

      // save the weights and other supplementary data
      _ <- {
        val newJsons = resultsAndJsons.map { case (results, json) =>
          val weightJson = JsObject(
            results.finalWeights.zipWithIndex.map { case (weight, index) =>
              ("rc_w_" + index, JsNumber(BigDecimal.valueOf(weight)))
            }
          )
          json ++ weightJson
        }

        dataSetService.saveOrUpdateRecords(weightDsa.dataSetRepo, newJsons.toSeq, None, false, None, Some(100))
      }

      // save filters
      _ <- weightDsa.filterRepo.save(
        Seq(mainFilter) ++ Seq(1, 10, 100, 1000).flatMap(filters(_, weightsCount))
      )

      // save views
      _ <- weightDsa.dataViewRepo.save(
        Seq(mainDataView(weightsCount), pdVsControlDataView(weightsCount))
      )
    } yield
      ()

  private def mainDataView(weightsCount: Int): DataView = {
    val weightFieldNames = (0 until weightsCount).map( index => ("rc_w_" + index) )

    val distributionWidgets = weightFieldNames.take(6).map(DistributionWidgetSpec(_, None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))))

    val boxPlotWidgets = weightFieldNames.take(6).map(BoxWidgetSpec(_))

    val correlationWidget = CorrelationWidgetSpec(
      fieldNames = weightFieldNames.take(15),
      displayOptions = BasicDisplayOptions(gridWidth = Some(6))
    )

    DataView(
      None, "Main", Nil,
      Seq("recordId") ++ weightFieldNames.take(5),
      distributionWidgets ++ boxPlotWidgets ++ Seq(correlationWidget),
      2,
      true
    )
  }

  private def pdVsControlDataView(weightsCount: Int): DataView = {
    val weightFieldNames = (0 until weightsCount).map( index => ("rc_w_" + index) )

    val distributionWidgets = weightFieldNames.take(10).map(DistributionWidgetSpec(_, None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))))

    val boxPlotWidgets = weightFieldNames.take(10).map(BoxWidgetSpec(_))

    val correlationWidget = CorrelationWidgetSpec(fieldNames = weightFieldNames.take(15))

    DataView(
      None,
      "PD vs Control",
      Seq(
        Left(Seq(diagnosisCondition(ConditionType.Equals, "true"))),
        Left(Seq(diagnosisCondition(ConditionType.Equals, "false")))
      ),
      Seq("recordId") ++ weightFieldNames.take(2),
      distributionWidgets ++ boxPlotWidgets ++ Seq(correlationWidget),
      12,
      false
    )
  }

  private def filters(maxWeight: Int, weightsCount: Int): Seq[Filter] = {
    val conditions = (0 until weightsCount).map { index =>
      val fieldName = "rc_w_" + index
      val gtCondition = FilterCondition(fieldName, None, ConditionType.Greater, (-maxWeight).toString, None)
      val ltCondition = FilterCondition(fieldName, None, ConditionType.Less, maxWeight.toString, None)
      Seq(gtCondition, ltCondition)
    }.flatten

    Seq(
      Filter(
        None,
        Some(s"Diagnosis Not Null (RC_W -$maxWeight to $maxWeight)"),
        conditions ++ Seq(diagnosisCondition(ConditionType.NotEquals, ""))
      ),
      Filter(
        None,
        Some(s"Diagnosis True (RC_W -$maxWeight to $maxWeight)"),
        conditions ++ Seq(diagnosisCondition(ConditionType.Equals, "true"))
      ),
      Filter(
        None,
        Some(s"Diagnosis False (RC_W -$maxWeight to $maxWeight)"),
        conditions ++ Seq(diagnosisCondition(ConditionType.Equals, "false"))
      )
    )
  }

  private def diagnosisCondition(conditionType: ConditionType.Value, value: String) =
    FilterCondition("professional-diagnosis", None, conditionType, value, None)

  private def mainFilter =
    Filter(
      None,
      Some("Diagnosis Not Null"),
      Seq(FilterCondition("professional-diagnosis", None, ConditionType.NotEquals, "", None))
    )

  private def calcErrors(
    results: Traversable[RCPredictionResults],
    lastNum: Int
  ): (Double, Double) = {
    val meanSamps = results.map { result =>
      if (result.sampErrors.size >= lastNum)
        Some(result.sampErrors.takeRight(lastNum))
      else
        None
    }.flatten.transpose.map(s => s.sum / s.size)

    val lastRnmses = results.map { result =>
      if (result.squareErrors.size >= lastNum) {
        val meanSquare = result.squareErrors.takeRight(lastNum).sum / lastNum
        if (meanSquare.isNaN || meanSquare.isInfinity) {
          println("Mean MSE is NaN or infinite")
          None
        } else if (result.targetVariance.isNaN || result.targetVariance.isInfinity) {
          println("Variance is NaN or infinite")
          None
        } else
          Some(math.sqrt(meanSquare / result.targetVariance))
      } else
        None
    }.flatten

    // TODO: check the calculation

    val lastRnmsesFinite = lastRnmses.filterNot(x => x.isNaN || x.isInfinity)
    if (lastRnmsesFinite.size != lastRnmses.size) {
      println(s" ${lastRnmses.size - lastRnmsesFinite.size} NaN or infinite RNMSEs found")
    }
    val meanRnmseLast = lastRnmsesFinite.sum / lastRnmsesFinite.size
    val meanSampLast = meanSamps.sum / lastNum

    (meanRnmseLast, meanSampLast)
  }

  private def saveResults(
    sourceDataSetMetaInfo: DataSetMetaInfo,
    item: RCPredictionSettingAndResults,
    resultDataSetId: String,
    resultDataSetName: String
  ): Future[Unit] =
    for {
      // register the results data set (if not registered already)
      newDsa <- dsaf.register(
        sourceDataSetMetaInfo.copy(_id = None, id = resultDataSetId, name = resultDataSetName, timeCreated = new ju.Date()),
        Some(resultDataSetSetting(resultDataSetId)),
        None
      )

      // update the dictionary
      _ <- dataSetService.updateDictionary(resultDataSetId, settingAndResultsFields, false, true)

      // save the results
      _ <- newDsa.dataSetRepo.save(Seq(Json.toJson(item).as[JsObject]))
    } yield
      ()

  private def reportResults(
    setting: ReservoirLearningSetting,
    resultVarianceAndJsons: Traversable[(RCPredictionResults, JsObject)]
  ) = {
    val lastNum = setting.getWeightAdaptationIterationNum

    val results = resultVarianceAndJsons.map(_._1)

    val meanSamps = results.map { x =>
      if (x.sampErrors.size >= lastNum)
        Some(x.sampErrors.takeRight(lastNum))
      else
        None
    }.flatten.transpose.map(s => s.sum / s.size)

    val lastRnmses = resultVarianceAndJsons.map { case (result, _) =>
      if (result.squareErrors.size >= lastNum) {
        val meanSquare = result.squareErrors.takeRight(lastNum).sum / lastNum
        Some(math.sqrt(meanSquare / result.targetVariance))
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
  finalWeights: Seq[jl.Double],
  targetVariance: Double
)