package services.ml

import java.util.Collections
import java.{lang => jl, util => ju}
import javax.inject.Inject

import _root_.util.{retry, seqFutures}
import com.banda.math.business.MathUtil
import com.banda.math.business.learning.{IOStream, IOStreamFactory}
import com.banda.math.domain.rand._
import com.banda.network.business._
import com.banda.network.business.learning.{ErrorMeasures, ReservoirTrainerFactory}
import com.banda.network.domain._
import com.google.inject.{ImplementedBy, Singleton}
import org.incal.core.dataaccess.Criterion.Infix
import dataaccess.JsonReadonlyRepoExtra._
import models._
import models.ml.RCPredictionSettingAndResults.rcPredictionSettingAndResultsFormat
import models.ml._
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.incal.core.{ConditionType, FilterCondition}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import util.FieldUtil.caseClassToFlatFieldTypes
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.ml.transformers.VectorColumnScalerNormalizer
import services.{DataSetService, SparkApp}

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
    preserveWeightFieldNames: Seq[String]
  ): Future[Unit]

  def predictSeries(
    topology: Topology,
    setting: ExtendedReservoirLearningSetting,
    json: JsObject,
    ioSpec: RCPredictionInputOutputSpec
  ): Future[Option[RCPredictionResults]]

  def transformSeriesJava(
    series: Seq[Seq[jl.Double]],
    transformType: VectorTransformType.Value
  ): Future[Seq[Seq[jl.Double]]]

  def transformSeries(
    series: Seq[Seq[Double]],
    transformType: VectorTransformType.Value
  ): Future[Seq[Seq[Double]]]
}

@Singleton
class RCPredictionServiceImpl @Inject()(
    reservoirTrainerFactory: ReservoirTrainerFactory,
    topologyFactory: TopologyFactory,
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService,
    sparkApp: SparkApp,
    ioStreamFactory: IOStreamFactory
  ) extends RCPredictionService {

  private val settingAndResultsFields =
    caseClassToFlatFieldTypes[RCPredictionSettingAndResults]("-").filter(_._1 != "_id")

  private val defaultBatchSize = 20

  private val logger = Logger

  override def predictAndStoreResults(
    setting: ExtendedReservoirLearningSetting,
    ioSpec: RCPredictionInputOutputSpec,
    batchSize: Option[Int],
    preserveWeightFieldNames: Seq[String]
  ): Future[Unit] = {
    val start = new ju.Date
    val dsa = dsaf(ioSpec.sourceDataSetId).get
    val dataSetRepo = dsa.dataSetRepo

    val inputDim = ioSpec.inputSeriesFieldPaths.size
    val outputDim = ioSpec.outputSeriesFieldPaths.size

    val topology = createTopology(setting, inputDim, outputDim)

    val seriesFieldNames = ioSpec.inputSeriesFieldPaths.map(_.split('.').head) ++ ioSpec.outputSeriesFieldPaths.map(_.split('.').head)

    val actualBatchSize = batchSize.getOrElse(defaultBatchSize)

    // helper function to get a single json batch
    def getJsons(ids: Traversable[BSONObjectID]) =
      dataSetRepo.findByIds(
        ids.head,
        actualBatchSize,
        (preserveWeightFieldNames ++ seriesFieldNames).toSet
      )

    // shared (readonly) job context
    val jobContext = sparkApp.sc.broadcast(
      RCPredictionJobContext(
        topology,
        topologyFactory,
        ioStreamFactory,
        reservoirTrainerFactory,
        setting
      )
    )

    def prepJobDataAndContext(
      json: JsObject,
      index: Int
    ): Future[Option[(Int, (RCPredictionJobData, Broadcast[RCPredictionJobContext]), JsObject)]] =
      for {
        inputOutputOption <- extractAndTransformIOSeries(json, ioSpec, setting.seriesPreprocessingType)
      } yield {
        inputOutputOption.map { case (input, output) =>
          val jobData = RCPredictionJobData(index, input, output)
          (index, (jobData, jobContext), json)
        }
      }

    import sparkApp.sqlContext.implicits._

    def runPredictions(
      jobDataContexts: Seq[(RCPredictionJobData, Broadcast[RCPredictionJobContext])],
      jobIndexJsonMap: Map[Int, JsObject]
    ): Future[Seq[(RCPredictionResults, JsObject)]] = {
      sparkApp.sc.parallelize(jobDataContexts).map { jobDataContext =>
        val jobData = jobDataContext._1
        val jobContext = jobDataContext._2.value

        val result = RCPredictionStaticHelper.predictTimeSeriesWithoutPreprocessing(
          jobContext.topologyFactory, jobContext.ioStreamFactory, jobContext.reservoirTrainerFactory, jobContext.setting, jobContext.topology, jobData.input, jobData.output
        )
        (jobData.index, result)
      }.collectAsync().map(_.map { case (index, results) =>
        val json = jobIndexJsonMap.get(index).get
        val otherDataJson = seriesFieldNames.foldLeft(json) { case (json, fieldName) => json.-(fieldName) }
        (results, otherDataJson)
      })
    }

    def runPredictions2(
      jobDatas: Seq[RCPredictionJobData],
      jobContextBroadcast: Broadcast[RCPredictionJobContext],
      jobIndexJsonMap: Map[Int, JsObject]
    ): Seq[(RCPredictionResults, JsObject)] = {
      sparkApp.session.createDataset(jobDatas).map { jobData =>
        val jobContext = jobContextBroadcast.value
        val result = RCPredictionStaticHelper.predictTimeSeriesWithoutPreprocessing(
          jobContext.topologyFactory, jobContext.ioStreamFactory, jobContext.reservoirTrainerFactory, jobContext.setting, jobContext.topology, jobData.input, jobData.output
        )
        (jobData.index, result)
      }.collect.map { case (index, results) =>
        val json = jobIndexJsonMap.get(index).get
        val otherDataJson = seriesFieldNames.foldLeft(json) { case (json, fieldName) => json.-(fieldName) }
        (results, otherDataJson)
      }
    }

    for {
      // retrieve the total count
      count <- dataSetRepo.count()

      // get all the ids
      ids <- dataSetRepo.allIds

      // predict the time series in batches and retrieve the results with jsons
      resultsAndJsons <-
        seqFutures(ids.toSeq.grouped(actualBatchSize).zipWithIndex) {

          case (ids, groupIndex) =>
            for {
              jsons <- retry("Retrieving of JSONs (in a batch) failed:", logger, 5)(getJsons(ids))

              // create a sequence of job data/contexts
              indexedJobData <- {
                logger.info(s"Processing time series ${groupIndex * actualBatchSize} to ${(jsons.size - 1) + (groupIndex * actualBatchSize)}")

                Future.sequence(
                  jsons.toSeq.zipWithIndex.map { case (json, index) =>
                    prepJobDataAndContext(json, index)
                  }
                ).map(_.flatten)
              }

              // run RC prediction simulations and collect the results
              resultsWithJsons <- {
                val jobData = indexedJobData.map(_._2)
                val jobIndexJsonMap = indexedJobData.map(x => (x._1, x._3)).toMap

                runPredictions(jobData, jobIndexJsonMap)
              }
            } yield {
              resultsWithJsons
//              val jobData = indexedJobData.map(_._2._1)
//              val jobIndexJsonMap = indexedJobData.map(x => (x._1, x._3)).toMap
//
//              runPredictions2(jobData, jobContext, jobIndexJsonMap)
            }
        }

      // flatten the results
      allResults = resultsAndJsons.flatten

      // get the info about the fields we want to save alongside the trained weights
      weightFields <- dsa.fieldRepo.find(Seq("name" #-> preserveWeightFieldNames))

      // save the weight matrix
      _ <- saveWeightDataSet(dsa, ioSpec.resultDataSetId, ioSpec.resultDataSetName, weightFields, allResults)

      // calc the errors
      (meanRnmse, meanSamp) = calcErrors(
        allResults.map(_._1),
        setting.getWeightAdaptationIterationNum
      )

      // get the data set name
      dataSetName <- dsa.dataSetName

      // save the results
      _ <- {
        val settingAndResults = RCPredictionSettingAndResults(
          None,
          RCPredictionSetting(
            setting.getReservoirNodeNum,
            setting.getReservoirInDegree,
            setting.getReservoirCircularInEdges,
            setting.getReservoirFunctionType,
            setting.getReservoirFunctionParams.map(_.map(_.toDouble)),
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

        saveResults(dsa, settingAndResults, ioSpec.sourceDataSetId + "_results", dataSetName + " Results")
      }
    } yield {
      println(s"RC prediction finished in ${new ju.Date().getTime - start.getTime} ms.")
      jobContext.unpersist()
      reportResults(setting, meanRnmse, meanSamp)
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
      setting.getReservoirCircularInEdges,
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
    json: JsObject,
    ioSpec: RCPredictionInputOutputSpec
  ): Future[Option[RCPredictionResults]] =
    for {
      // prepare input and output for the prediction task
      inputOutputOption <- extractAndTransformIOSeries(json, ioSpec, setting.seriesPreprocessingType)
    } yield
      // run the prediction
      inputOutputOption.map { inputOutput =>
        RCPredictionStaticHelper.predictTimeSeriesWithoutPreprocessing(
          topologyFactory, ioStreamFactory, reservoirTrainerFactory, setting, topology, inputOutput._1, inputOutput._2
        )
      }

  private def extractIOSeries(
    json: JsObject,
    ioSpec: RCPredictionInputOutputSpec
  ): Option[(Seq[Seq[jl.Double]], Seq[jl.Double])] = {
    // TODO: only the first output series path is taken
    IOSeriesUtil(
      json,
      IOJsonTimeSeriesSpec(
        ioSpec.inputSeriesFieldPaths,
        ioSpec.outputSeriesFieldPaths.head,
        ioSpec.dropLeftLength,
        ioSpec.dropRightLength,
        ioSpec.seriesLength
      )
    ).map { case (input, output) =>
      val jlInput = input.map(_.map(x => x: jl.Double))
      val jlOutput = output.map(x => x: jl.Double)
      (jlInput, jlOutput)
    }
  }

  // note that empty input or output series are return as None
  private def extractAndTransformIOSeries(
    json: JsObject,
    ioSpec: RCPredictionInputOutputSpec,
    seriesPreprocessingType: Option[VectorTransformType.Value]
  ): Future[Option[(Seq[Seq[jl.Double]], Seq[jl.Double])]] =
    extractIOSeries(json, ioSpec).map { case (inputSeries, outputSeries) =>
      // check the overlap between input and output paths to see if the output transformation can be skipped
      val inputFieldPathIndexMap = ioSpec.inputSeriesFieldPaths.zipWithIndex.toMap
      val outputInputIndexes: Seq[Int] = ioSpec.outputSeriesFieldPaths.flatMap(inputFieldPathIndexMap.get(_))
      val outputIncludedInInput = outputInputIndexes.size == ioSpec.outputSeriesFieldPaths.size

      if (inputSeries.nonEmpty) {
        val inputOutputFuture =
          seriesPreprocessingType.map { transformType =>
            for {
              // transform input
              input <-
                RCPredictionStaticHelper.transformSeriesJava(sparkApp.session)(inputSeries, transformType)

              // transform output
              output <-
                if (outputIncludedInInput) {
                  val output = input.map(seq => outputInputIndexes.map(seq(_)))
                  Future(output)
                } else
                  RCPredictionStaticHelper.transformSeriesJava(sparkApp.session)(outputSeries.map(Seq(_)), transformType)
            } yield {
              (input, output.map(_.head))
            }
          }.getOrElse {
            Future((inputSeries, outputSeries))
          }

        inputOutputFuture.map(Some(_))
      } else
        Future(None)
    }.getOrElse(
      Future(None)
    )

  private def saveWeightDataSet(
    sourceDsa: DataSetAccessor,
    resultDataSetId: String,
    resultDataSetName: String,
    fields: Traversable[Field],
    resultsAndJsons: Traversable[(RCPredictionResults, JsObject)]
  ): Future[Unit] =
    for {
      // register the weight output data set
      weightDsa <- dataSetService.register(sourceDsa, resultDataSetId, resultDataSetName, StorageType.ElasticSearch)

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

    val boxPlotWidgets = weightFieldNames.take(6).map(BoxWidgetSpec(_, None))

    val correlationWidget = CorrelationWidgetSpec(
      fieldNames = weightFieldNames.take(15),
      correlationType = CorrelationType.Pearson,
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

    val boxPlotWidgets = weightFieldNames.take(10).map(BoxWidgetSpec(_, None))

    val correlationWidget = CorrelationWidgetSpec(
      fieldNames = weightFieldNames.take(15),
      correlationType = CorrelationType.Pearson
    )

    DataView(
      None,
      "PD vs Control",
      Seq(
        Left(Seq(diagnosisCondition(ConditionType.Equals, Some("true")))),
        Left(Seq(diagnosisCondition(ConditionType.Equals, Some("false"))))
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
      val gtCondition = FilterCondition(fieldName, None, ConditionType.Greater, Some((-maxWeight).toString), None)
      val ltCondition = FilterCondition(fieldName, None, ConditionType.Less, Some(maxWeight.toString), None)
      Seq(gtCondition, ltCondition)
    }.flatten

    Seq(
      Filter(
        None,
        Some(s"Diagnosis Not Null (RC_W -$maxWeight to $maxWeight)"),
        conditions ++ Seq(diagnosisCondition(ConditionType.NotEquals, None))
      ),
      Filter(
        None,
        Some(s"Diagnosis True (RC_W -$maxWeight to $maxWeight)"),
        conditions ++ Seq(diagnosisCondition(ConditionType.Equals, Some("true")))
      ),
      Filter(
        None,
        Some(s"Diagnosis False (RC_W -$maxWeight to $maxWeight)"),
        conditions ++ Seq(diagnosisCondition(ConditionType.Equals, Some("false")))
      )
    )
  }

  private def diagnosisCondition(conditionType: ConditionType.Value, value: Option[String]) =
    FilterCondition("professional-diagnosis", None, conditionType, value, None)

  private def mainFilter =
    Filter(
      None,
      Some("Diagnosis Not Null"),
      Seq(FilterCondition("professional-diagnosis", None, ConditionType.NotEquals, None, None))
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
      logger.warn(s" ${lastRnmses.size - lastRnmsesFinite.size} NaN or infinite RNMSEs found")
    }
    val meanRnmseLast = lastRnmsesFinite.sum / lastRnmsesFinite.size
    val meanSampLast = meanSamps.sum / lastNum

    (meanRnmseLast, meanSampLast)
  }

  private def saveResults(
    sourceDsa: DataSetAccessor,
    item: RCPredictionSettingAndResults,
    resultDataSetId: String,
    resultDataSetName: String
  ): Future[Unit] =
    for {
      // register the results data set (if not registered already)
      newDsa <- dataSetService.register(sourceDsa, resultDataSetId, resultDataSetName, StorageType.ElasticSearch)

      // update the dictionary
      _ <- dataSetService.updateDictionary(resultDataSetId, settingAndResultsFields, false, true)

      // save the results
      _ <- newDsa.dataSetRepo.save(Json.toJson(item).as[JsObject])
    } yield
      ()

  private def reportResults(
    setting: ReservoirLearningSetting,
    meanRnmse: Double,
    meanSamp: Double
  ) = {
    val lastNum = setting.getWeightAdaptationIterationNum

    logger.info("In scale                      : " + setting.getInScale)
    logger.info("Reservoir node #              : " + setting.getReservoirNodeNum)

    if (setting.getReservoirInDegree.isDefined)
      logger.info("Reservoir in-degree           : " + setting.getReservoirInDegree.get)

    if (setting.getReservoirPreferentialAttachment)
      logger.info("Reservoir pref attachment     : " + setting.getReservoirPreferentialAttachment)

    logger.info("Reservoir bias                : " + setting.getReservoirBias)

    if (setting.getReservoirInDegreeDistribution.isDefined) {
      val distribution = setting.getReservoirInDegreeDistribution.get.asInstanceOf[ShapeLocationDistribution[jl.Double]]
      logger.info("Reservoir in-degree dist    : " + distribution.getLocation + ", " + distribution.getShape)
    }

    if (setting.getReservoirEdgesNum.isDefined)
      logger.info("Reservoir edges num           : " + setting.getReservoirEdgesNum.get)

    logger.info("Input-Reservoir Connect       : " + setting.getInputReservoirConnectivity)

    if (setting.getWeightDistribution.isInstanceOf[ShapeLocationDistribution[jl.Double]])
      println("Weight Distribution STD       : " + setting.getWeightDistribution.asInstanceOf[ShapeLocationDistribution[jl.Double]].getShape)

    logger.info("Spectral radius               : " + setting.getReservoirSpectralRadius)
    logger.info("Reservoir function            : " + setting.getReservoirFunctionType)
    logger.info("---------------------------------------------")
    logger.info("Mean RNMSE                    : " + meanRnmse)
    logger.info("Mean SAMP                     : " + meanSamp)
  }

  override def transformSeriesJava(
    series: Seq[Seq[jl.Double]],
    transformType: VectorTransformType.Value
  ): Future[Seq[Seq[jl.Double]]] =
    RCPredictionStaticHelper.transformSeriesJava(sparkApp.session)(series, transformType)

  override def transformSeries(
    series: Seq[Seq[Double]],
    transformType: VectorTransformType.Value
  ): Future[Seq[Seq[Double]]] =
    RCPredictionStaticHelper.transformSeries(sparkApp.session)(series, transformType)
}

object RCPredictionStaticHelper extends Serializable {

  def predictTimeSeriesWithoutPreprocessing(
    topologyFactory: TopologyFactory,
    ioStreamFactory: IOStreamFactory,
    reservoirTrainerFactory: ReservoirTrainerFactory,
    setting: ExtendedReservoirLearningSetting,
    topology: Topology,
    inputSeries: Seq[Seq[jl.Double]],
    targetSeries: Seq[jl.Double]
  ): RCPredictionResults = {
    val iterationNum = targetSeries.size - setting.predictAhead - setting.getWashoutPeriod

    // get reservoir and output nodes
    val initializedTopology =
      if (topology.hasLayers && !topology.isTemplate && !topology.isSpatial)
        topology
      else
        topologyFactory(topology)

    val layers = initializedTopology.getLayers.toSeq
    val reservoirNodes = new ju.ArrayList(layers(1).getAllNodes)
    Collections.sort(reservoirNodes)
    val outputNodes = new ju.ArrayList(layers(2).getAllNodes)
    Collections.sort(outputNodes)

    def createIOStream = ioStreamFactory.createInstancePredict(setting.predictAhead, setting.getWashoutPeriod)(inputSeries, targetSeries)

    val call = { ioStream: IOStream[jl.Double] =>
      val (predictor, weightAccessor) = reservoirTrainerFactory(initializedTopology, setting, ioStream, iterationNum)

      predictor.train(iterationNum)
      val outputs = predictor.outputs
      val desiredOutputs = (ioStream.outputStream take outputs.size).toList.map(_.head)

      val squares = ErrorMeasures.calcSquares(outputs, desiredOutputs)
      val samps = ErrorMeasures.calcSamps(outputs, desiredOutputs)

      val weights: Seq[jl.Double] = {
        for {
          reservoirNode <- reservoirNodes
          outputNode <- outputNodes
        } yield
          weightAccessor.getWeight(reservoirNode, outputNode)
      }.flatten

      val targetVariance = MathUtil.calcStats(0, targetSeries).getVariance.toDouble

      RCPredictionResults(squares, samps, outputs.toSeq, desiredOutputs, weights, targetVariance)
    }

    val ioStream = createIOStream
    call(ioStream)
  }

  def transformSeriesJava(
    session: SparkSession)(
    series: Seq[Seq[jl.Double]],
    transformType: VectorTransformType.Value
  ): Future[Seq[Seq[jl.Double]]] = {
    val javaSeries: Seq[Seq[Double]] = series.map(_.map(_.toDouble))

    for {
      outputSeries <- transformSeries(session)(javaSeries, transformType)
    } yield
      outputSeries.map(_.map(jl.Double.valueOf(_)))
  }

  def transformSeries(
    session: SparkSession)(
    inputSeries: Seq[Seq[Double]],
    transformType: VectorTransformType.Value
  ): Future[Seq[Seq[Double]]] = {
    val rows = inputSeries.zipWithIndex.map { case (oneSeries, index) =>
      (index, Vectors.dense(oneSeries.toArray))
    }

    val df = session.createDataFrame(rows).toDF("id", "features")
    val newDf = VectorColumnScalerNormalizer(transformType).fit(df).transform(df)

    for {
      rows <- newDf.select("scaledFeatures").rdd.collectAsync()
    } yield {
      newDf.unpersist()
      df.unpersist()

      rows.map(row =>
        row.getAs[Vector](0).toArray: Seq[Double]
      )
    }
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

case class RCPredictionJobData (
  index: Int,
  input: Seq[Seq[jl.Double]],
  output: Seq[jl.Double]
)

case class RCPredictionJobContext (
  topology: Topology,
  topologyFactory: TopologyFactory,
  ioStreamFactory: IOStreamFactory,
  reservoirTrainerFactory: ReservoirTrainerFactory,
  setting: ExtendedReservoirLearningSetting
)