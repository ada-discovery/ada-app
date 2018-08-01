package services.ml

import javax.inject.{Inject, Singleton}
import java.{lang => jl, util => ju}

import util.parallelize
import com.google.inject.ImplementedBy
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{AdaException, Field, FieldTypeId, FieldTypeSpec}
import models.ml.classification.Classification
import models.ml.regression.Regression
import models.ml.unsupervised.UnsupervisedLearning
import models.ml.{ClassificationEvalMetric, _}
import util.{GroupMapList, STuple3}
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, Evaluator, MulticlassClassificationEvaluator, RegressionEvaluator}
import org.apache.spark.ml.feature._
import org.apache.spark.ml._
import org.apache.spark.sql.types.{Metadata, MetadataBuilder, StructType, _}
import org.apache.spark.sql.{DataFrame, Dataset, Row}
import org.apache.spark.ml.clustering._
import org.apache.spark.ml.linalg.{DenseVector, Vector, Vectors}
import org.apache.spark.ml.param._
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.sql.functions.col
import play.api.libs.json.{JsObject, Json}
import services.SparkApp
import play.api.{Configuration, Logger}
import services.ml.transformers._
import services.stats.StatsService

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

@ImplementedBy(classOf[MachineLearningServiceImpl])
trait MachineLearningService {

  def classify(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value] = LearningSetting[ClassificationEvalMetric.Value](),
    replicationData: Traversable[JsObject] = Nil,
    binCurvesNumBins: Option[Int] = None
  ): Future[ClassificationResultsHolder]

  def classifyWithDelayLine(
    data: JsObject,
    inputSeriesFieldPaths: Seq[String],
    outputSeriesFieldPath: String,
    dlSize: Int,
    predictAhead: Int,
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value] = LearningSetting[ClassificationEvalMetric.Value](),
    replicationData: Option[JsObject] = None,
    binCurvesNumBins: Option[Int] = None
  ): Future[ClassificationResultsHolder]

  def regress(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value] = LearningSetting[RegressionEvalMetric.Value](),
    replicationData: Traversable[JsObject] = Nil
  ): Future[RegressionResultsHolder]

  def regressSeriesWithDelayLine(
    data: JsObject,
    inputSeriesFieldPaths: Seq[String],
    outputSeriesFieldPath: String,
    dlSize: Int,
    predictAhead: Int,
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value] = LearningSetting[RegressionEvalMetric.Value](),
    replicationData: Option[JsObject] = None
  ): Future[RegressionResultsHolder]

  def cluster(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int] = None
  ): Traversable[(String, Int)]

  def clusterDf(
    dataFrame: DataFrame,
    idColumnName: String,
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int] = None
  ): Traversable[(String, Int)]

  def clusterAndGetPCA12(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int] = None
  ): (Traversable[(String, Int)], Traversable[(String, (Double, Double))])

  def pcaComponents(
    k: Int)(
    df: DataFrame
  ): DataFrame

  // AFTSurvivalRegression
  // IsotonicRegression
}

@Singleton
private class MachineLearningServiceImpl @Inject() (
    sparkApp: SparkApp,
    configuration: Configuration,
    statsService: StatsService
  ) extends MachineLearningService {

  private val logger = Logger // (this.getClass())

  private val session = sparkApp.session
  private implicit val sqlContext = sparkApp.sqlContext

  private val defaultTrainingTestingSplit = 0.8
  private val defaultClassificationCrossValidationEvalMetric = ClassificationEvalMetric.accuracy
  private val defaultRegressionCrossValidationEvalMetric = RegressionEvalMetric.rmse

  private val repetitionParallelism = configuration.getInt("ml.repetition_parallelism").getOrElse(2)
  private val binaryClassifierInputName = configuration.getString("ml.binary_classifier.input").getOrElse("probability")
  private val binaryPredictionVectorizer = new IndexVectorizer() {
    setInputCol("prediction"); setOutputCol(binaryClassifierInputName)
  }

  private val randomSplit = (splitRatio: Double) => (dataFrame: DataFrame) => {
    val Array(training, test) = dataFrame.randomSplit(Array(splitRatio, 1 - splitRatio))
    (training, test)
  }

  private val seqSplit = (orderColumn: String) => (splitRatio: Double) => (df: DataFrame) => {
    val splitValue = df.stat.approxQuantile(orderColumn, Array(splitRatio), 0.001)(0)
    val headDf = df.where(df(orderColumn) < splitValue)
    val tailDf = df.where(df(orderColumn) >= splitValue)
    (headDf, tailDf)
  }

  private val useConsecutiveOrderForDL = configuration.getBoolean("ml.dl_use_consecutive_order_transformers").getOrElse(false)

  private val classificationEvaluators =
    ClassificationEvalMetric.values.filter(metric =>
      metric != ClassificationEvalMetric.areaUnderPR && metric != ClassificationEvalMetric.areaUnderROC
    ).toSeq.map { metric =>
      val evaluator = new MulticlassClassificationEvaluator()
        .setLabelCol("label")
        .setPredictionCol("prediction")
        .setMetricName(metric.toString)

      EvaluatorWrapper(metric, evaluator)
    }

  private val binClassificationEvaluators =
    Seq(ClassificationEvalMetric.areaUnderPR, ClassificationEvalMetric.areaUnderROC).map { metric =>
      val evaluator = new BinaryClassificationEvaluator()
        .setLabelCol("label")
        .setRawPredictionCol(binaryClassifierInputName)
        .setMetricName(metric.toString)

      EvaluatorWrapper(
        metric,
        evaluator
      )
    }

  private val regressionEvaluators = RegressionEvalMetric.values.toSeq.map { metric =>
    val evaluator = new RegressionEvaluator()
      .setLabelCol("label")
      .setPredictionCol("prediction")
      .setMetricName(metric.toString)

    EvaluatorWrapper(metric, evaluator)
  }

  override def classify(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value],
    replicationData: Traversable[JsObject],
    binCurvesNumBins: Option[Int]
  ): Future[ClassificationResultsHolder] = {
    // training, test, and replication data

    // aux function to create a data frame
    def crateDataFrame(jsons: Traversable[JsObject]) = {
      val df = FeaturesDataFrameFactory(session, jsons, fields, Some(outputFieldName))
      BooleanLabelIndexer(Some("labelString")).transform(df)
    }

    // create a training/test data frame with all the features
    val df = crateDataFrame(data)
    // create a replication data frame with all the features
    val replicationDf = if (replicationData.nonEmpty) Some(crateDataFrame(replicationData)) else None

    // classify with a random split
    classifyAux(df, replicationDf, mlModel, setting, binCurvesNumBins, randomSplit, Nil, Nil)
  }

  def classifyWithDelayLine(
    data: JsObject,
    inputSeriesFieldPaths: Seq[String],
    outputSeriesFieldPath: String,
    dlSize: Int,
    predictAhead: Int,
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value] = LearningSetting[ClassificationEvalMetric.Value](),
    replicationData: Option[JsObject] = None,
    binCurvesNumBins: Option[Int] = None
  ): Future[ClassificationResultsHolder] = {
    // training, test, and replication data

    // aux function to create a data frame
    def crateDataFrame(jsons: JsObject) = {
      val df = FeaturesDataFrameFactory.applySeries(session)(data, inputSeriesFieldPaths, outputSeriesFieldPath)
      BooleanLabelIndexer(Some("labelString")).transform(df)
    }

    // create a training/test data frame with all the features
    val df = crateDataFrame(data)
    // create a replication data frame with all the features
    val replicationDf = replicationData.map(crateDataFrame)

    // delay line transformers
    val dlTransformers = createDelayLineTransformers(dlSize, predictAhead)

    // classify with the DL transformers and a sequential split
    classifyAux(df, replicationDf, mlModel, setting, binCurvesNumBins, seqSplit("index"), Nil, dlTransformers)
  }

  private def classifyAux(
    df: DataFrame,
    replicationDf: Option[DataFrame],
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value],
    binCurvesNumBins: Option[Int],
    split: Double => (DataFrame => (DataFrame, DataFrame)),
    initStages: Seq[_ <: PipelineStage],
    preTrainingStages: Seq[_ <: PipelineStage]
  ): Future[ClassificationResultsHolder] = {
    // stages
    val coreStages = classificationStages(setting)
    val stages = initStages ++ coreStages ++ preTrainingStages

    // classify with the stages
    classifyAux(df, replicationDf, mlModel, setting, binCurvesNumBins, split, stages)
  }

  private def classifyAux(
    df: DataFrame,
    replicationDf: Option[DataFrame],
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value],
    binCurvesNumBins: Option[Int],
    split: Double => (DataFrame => (DataFrame, DataFrame)),
    stages: Seq[_ <: PipelineStage]
  ): Future[ClassificationResultsHolder] = {

    // cache the data frames
    df.cache()
    if (replicationDf.isDefined)
      replicationDf.get.cache()

    // CREATE A TRAINER

    val originalFeaturesType = df.schema.fields.find(_.name == "features").get
    val originalInputSize = originalFeaturesType.metadata.getMetadata("ml_attr").getLong("num_attrs").toInt
    val inputSize = setting.pcaDims.getOrElse(originalInputSize)

    val outputLabelType = df.schema.fields.find(_.name == "label").get
    val outputSize = outputLabelType.metadata.getMetadata("ml_attr").getStringArray("vals").length

    val (trainer, paramMaps) = SparkMLEstimatorFactory(mlModel, inputSize, outputSize)
    val fullTrainer = new Pipeline().setStages((stages ++ Seq(trainer)).toArray)

    // REPEAT THE TRAINING-TEST CYCLE

    // split for the data into training and test parts
    val splitRatio = setting.trainingTestingSplit.getOrElse(defaultTrainingTestingSplit)

    // evaluators
    val evaluators = classificationEvaluators ++ (if (outputSize == 2) binClassificationEvaluators else Nil)

    // cross-validation evaluator
    val crossValidationEvaluator =
      setting.crossValidationEvalMetric.flatMap(metric =>
        evaluators.find(_.metric == metric)
      ).getOrElse(
        evaluators.find(_.metric == defaultClassificationCrossValidationEvalMetric).get
      )

    val count = df.count()

    val resultHoldersFuture = parallelize(1 to setting.repetitions.getOrElse(1), repetitionParallelism) { index =>
      logger.info(s"Execution of repetition $index started for $count rows.")
      val (training, test) = split(splitRatio)(df)

      training.cache()
      test.cache()

      // classify and evaluate
      val results = classifyAndEvaluate(
        fullTrainer,
        paramMaps,
        evaluators,
        crossValidationEvaluator.evaluator,
        setting.crossValidationFolds,
        outputSize,
        count,
        binCurvesNumBins,
        training,
        test,
        replicationDf
      )

      training.unpersist()
      test.unpersist()

      results
    }

    // CREATE FINAL PERFORMANCE RESULTS

    resultHoldersFuture.map { resultHolders =>
      // uncache
      df.unpersist
      if (replicationDf.isDefined)
        replicationDf.get.unpersist

      // create performance results
      val results = resultHolders.flatMap(_.evalResults)
      val performanceResults = results.groupBy(_._1).map { case (evalMetric, results) =>
        ClassificationPerformance(evalMetric, results.map { case (_, trainResult, testResults) =>
          val replicationResult = testResults.tail.headOption
          (trainResult, testResults.head, replicationResult)
        })
      }

      // counts
      val counts = resultHolders.map(_.count)

      // curves
      val curves = resultHolders.map { resultHolder =>
        (
          resultHolder.binTrainingCurves,
          resultHolder.binTestCurves.head,
          resultHolder.binTestCurves.tail.headOption.flatten
        )
      }

      ClassificationResultsHolder(performanceResults, counts, curves)
    }
  }

  override def regress(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value],
    replicationData: Traversable[JsObject]
  ): Future[RegressionResultsHolder] = {
    // training, test, and replication data

    // aux function to create a data frame
    def crateDataFrame(jsons: Traversable[JsObject]) =
      FeaturesDataFrameFactory(session, jsons, fields, Some(outputFieldName))

    // create a training/test data frame with all the features
    val df = crateDataFrame(data)
    // create a replication data frame with all the features
    val replicationDf = if (replicationData.nonEmpty) Some(crateDataFrame(replicationData)) else None

    // REGRESS
    regressAux(df, replicationDf, mlModel, setting, randomSplit, Nil, Nil)
  }

  override def regressSeriesWithDelayLine(
    data: JsObject,
    inputSeriesFieldPaths: Seq[String],
    outputSeriesFieldPath: String,
    dlSize: Int,
    predictAhead: Int,
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value] = LearningSetting[RegressionEvalMetric.Value](),
    replicationData: Option[JsObject]
  ): Future[RegressionResultsHolder] = {
    // training, test, and replication data

    // aux function to create a data frame
    def crateDataFrame(jsons: JsObject) =
      FeaturesDataFrameFactory.applySeries(session)(data, inputSeriesFieldPaths, outputSeriesFieldPath)

    // create a training/test data frame with all the features
    val df = crateDataFrame(data)
    // create a replication data frame with all the features
    val replicationDf = replicationData.map(crateDataFrame)

    // delay line transformers
    val dlTransformers = createDelayLineTransformers(dlSize, predictAhead)
    val showDf = SchemaUnchangedTransformer { df: DataFrame => df.orderBy("index").show(false); df }

    df.show(false)

    // regress with the DL transformers and a sequential split
    regressAux(df, replicationDf, mlModel, setting, seqSplit("index"), Nil, dlTransformers ++ Seq(showDf), true)
  }

  private def createDelayLineTransformers(
    windowSize: Int,
    labelShift: Int
  ) =
    if (useConsecutiveOrderForDL)
      Seq(
        SlidingWindowWithConsecutiveOrder.applyInPlace(windowSize, "features", "index"),
        SeqShiftWithConsecutiveOrder.applyInPlace(labelShift, "label", "index")
      )
    else
      Seq(
        SlidingWindow.applyInPlace(windowSize, "features", "index"),
        SeqShift.applyInPlace(labelShift, "label", "index")
      )

  private def regressAux(
    df: DataFrame,
    replicationDf: Option[DataFrame],
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value],
    split: Double => (DataFrame => (DataFrame, DataFrame)),
    initStages: Seq[_ <: PipelineStage],
    preTrainingStages: Seq[_ <: PipelineStage],
    collectOutputs: Boolean = false
  ): Future[RegressionResultsHolder] = {
    // stages
    val coreStages = regressionStages(setting)
    val stages = initStages ++ coreStages ++ preTrainingStages

    // regress with the stages
    regressAux(df, replicationDf, mlModel, setting, split, stages, collectOutputs)
  }

  private def regressAux(
    df: DataFrame,
    replicationDf: Option[DataFrame],
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value],
    split: Double => (DataFrame => (DataFrame, DataFrame)),
    stages: Seq[_ <: PipelineStage],
    collectOutputs: Boolean
  ): Future[RegressionResultsHolder] = {
    // CREATE A TRAINER

    val (trainer, paramMaps) = SparkMLEstimatorFactory(mlModel)
    val fullTrainer = new Pipeline().setStages((stages ++ Seq(trainer)).toArray)

    // REPEAT THE TRAINING-TEST CYCLE

    // split ratio for the data into training and test parts
    val splitRatio = setting.trainingTestingSplit.getOrElse(defaultTrainingTestingSplit)

    // cross-validation evaluator
    val crossValidationEvaluator =
      setting.crossValidationEvalMetric.flatMap(metric =>
        regressionEvaluators.find(_.metric == metric)
      ).getOrElse(
        regressionEvaluators.find(_.metric == defaultRegressionCrossValidationEvalMetric).get
      )

    val count = df.count()

    val resultHoldersFuture = parallelize(1 to setting.repetitions.getOrElse(1), repetitionParallelism) { index =>
      logger.info(s"Execution of repetition $index started for $count rows.")
      val (training, test) = split(splitRatio)(df)
      logger.info("Dataset split into training and test parts as: " + training.count() + " / " + test.count())

      training.cache()
      test.cache()

      val testSets = Seq(Some(test), replicationDf).flatten

      // run the trainer (with folds) on the given training and test data sets
      val (trainPredictions, testPredictions) = trainWithCrossValidation(
        fullTrainer, paramMaps, crossValidationEvaluator.evaluator, setting.crossValidationFolds, training, testSets
      )

      // evaluate the performance
      val results = evaluate(regressionEvaluators, trainPredictions, testPredictions)

      // unpersist and return the results
      training.unpersist
      test.unpersist

      // collect the actual vs expected outputs (if needed)
      val outputs: Traversable[Seq[(Double, Double)]] =
        if (collectOutputs) {
          val trainingOutputs = collectLabelPredictions(trainPredictions)
          val testOutputs = testPredictions.map(collectLabelPredictions)
          Seq(trainingOutputs) ++ testOutputs
        } else
          Nil

      RegressionResultsAuxHolder(results, count, outputs)
    }

    // EVALUATE PERFORMANCE

    resultHoldersFuture.map { resultHolders =>
      // uncache
      df.unpersist
      if (replicationDf.isDefined)
        replicationDf.get.unpersist

      // create performance results
      val results = resultHolders.flatMap(_.evalResults)
      val performanceResults = results.groupBy(_._1).map { case (evalMetric, results) =>
        RegressionPerformance(evalMetric, results.map { case (_, trainResult, testResults) =>
          val replicationResult = testResults.tail.headOption
          (trainResult, testResults.head, replicationResult)
        })
      }

      // counts
      val counts = resultHolders.map(_.count)

      // actual vs expected outputs
      val expectedAndActualOutputs = resultHolders.map(_.expectedAndActualOutputs)

      RegressionResultsHolder(performanceResults, counts, expectedAndActualOutputs)
    }
  }

  private def collectLabelPredictions(dataFrame: DataFrame) = {
    val df =
      if (dataFrame.columns.find(_.equals("index")).isDefined)
        dataFrame.orderBy("index")
      else
        dataFrame

    df.select("label", "prediction")
      .collect().toSeq
      .map { row => (row.getDouble(0), row.getDouble(1)) }
  }

  private def classifyAndEvaluate[M <: Model[M]](
    trainer: Estimator[M],
    paramMaps: Array[ParamMap],
    evaluators: Seq[EvaluatorWrapper[ClassificationEvalMetric.Value]],
    crossValidationEvaluator: Evaluator,
    folds: Option[Int],
    outputSize: Int,
    count: Long,
    binCurvesNumBins: Option[Int],
    trainingDf: DataFrame,
    testDf: DataFrame,
    replicationDf: Option[DataFrame]
  ): ClassificationResultsAuxHolder = {

    // run the trainer (with folds) on the given training and test data sets (replication df is treated as "another" test data set if provided)

    val testSets = Seq(Some(testDf), replicationDf).flatten

    val (trainingPredictions, testPredictions) = trainWithCrossValidation(
      trainer, paramMaps, crossValidationEvaluator, folds, trainingDf, testSets
    )

    // evaluate the performance

    def withBinaryEvaluationCol(df: DataFrame) =
      if (outputSize == 2 && !df.columns.contains(binaryClassifierInputName)) {
        binaryPredictionVectorizer.transform(df)
      } else
        df

    trainingPredictions.cache()
    testPredictions.foreach(_.cache())

    val trainingPredictionsExt = withBinaryEvaluationCol(trainingPredictions)
    val testPredictionsExt = testPredictions.map(withBinaryEvaluationCol)

    val results = evaluate(evaluators, trainingPredictionsExt, testPredictionsExt)

    // generate binary classification curves (roc, pr, etc.) if the output is binary

    val (binTrainingCurves, binTestCurves) =
      if (outputSize == 2) {
        // is binary
        val trainingCurves = binaryMetricsCurves(trainingPredictionsExt, binCurvesNumBins)
        val testCurves = testPredictionsExt.map(binaryMetricsCurves(_, binCurvesNumBins))
        (trainingCurves, testCurves)
      } else
        (None, testPredictionsExt.map(_ => None))

    // unpersist and return the results

    trainingPredictions.unpersist
    testPredictions.foreach(_.unpersist)

    ClassificationResultsAuxHolder(results, count, binTrainingCurves, binTestCurves)
  }

  private def classificationStages(
    setting: LearningSetting[_]
  ): Seq[_ <: PipelineStage] = {
    // normalize the features
    val normalize = setting.featuresNormalizationType.map(VectorColumnScalerNormalizer.applyInPlace(_, "features"))

    // reduce the dimensionality if needed
    val reduceDim = setting.pcaDims.map(InPlacePCA(_))

    // keep the label as string for sampling (if needed)
    val keepLabelString = IndexToStringIfNeeded("label", "labelString")

    // sampling
    val sample = SamplingTransformer(setting.samplingRatios)

    // sequence the stages and return
    val preStages = Seq(normalize, reduceDim).flatten
    if (setting.samplingRatios.nonEmpty) preStages ++ Seq(keepLabelString, sample) else preStages
  }

  private def regressionStages(
    setting: LearningSetting[_]
  ): Seq[_ <: PipelineStage] = {
    // normalize the features
    val normalize = setting.featuresNormalizationType.map(VectorColumnScalerNormalizer.applyInPlace(_, "features"))

    // reduce the dimensionality if needed
    val reduceDim = setting.pcaDims.map(InPlacePCA(_))

    // sequence the stages and return
    Seq(normalize, reduceDim).flatten
  }

  override def cluster(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int]
  ): Traversable[(String, Int)] = {
    val (df, idClusters) = clusterAux(data, fields, mlModel, featuresNormalizationType, pcaDim)
    idClusters
  }

  override def clusterAndGetPCA12(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int]
  ): (Traversable[(String, Int)], Traversable[(String, (Double, Double))]) = {
    val (df, idClusters) = clusterAux(data, fields, mlModel, featuresNormalizationType, pcaDim)

    // reduce the dimensionality if needed
    val pca12Df = InPlacePCA(2).fit(df).transform(df)

    import sparkApp.session.implicits._

    val idPca12Values = pca12Df.select(JsObjectIdentity.name, "features").map { r =>
      val id = r(0).asInstanceOf[String]
      val values = r(1).asInstanceOf[DenseVector].values
      (id, (values(0), values(1)))
    }.collect

    (idClusters, idPca12Values)
  }

  override def clusterDf(
    dataFrame: DataFrame,
    idColumnName: String,
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int] = None
  ): Traversable[(String, Int)] = {
    val featureNames = dataFrame.columns.filterNot(_.equals(idColumnName))
    val featureDf = dataFrame.transform(
      FeaturesDataFrameFactory.prepFeaturesDataFrame(featureNames.toSet, None)
    )

    val (df, idClusters) = clusterAux2(featureDf, idColumnName, mlModel, featuresNormalizationType, pcaDim)
    idClusters
  }

  private def clusterAux(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int]
  ): (DataFrame, Traversable[(String, Int)]) = {
    // prepare a data frame for learning
    val featureFieldNames = fields.map(_._1)
    val fieldsWithId = fields ++ Seq((JsObjectIdentity.name, FieldTypeSpec(FieldTypeId.String)))
    val df = FeaturesDataFrameFactory(session, data, fieldsWithId, featureFieldNames)

    clusterAux2(df, JsObjectIdentity.name, mlModel, featuresNormalizationType, pcaDim)
  }

  private def clusterAux2[M <: Model[M]](
    df: DataFrame,
    idColumnName: String,
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int]
  ): (DataFrame, Traversable[(String, Int)]) = {
    val trainer = SparkMLEstimatorFactory[M](mlModel)

    // normalize
    val normalize = featuresNormalizationType.map(VectorColumnScalerNormalizer.applyInPlace(_, "features"))

    // reduce the dimensionality if needed
    val reduceDim = pcaDim.map(InPlacePCA(_))

    val stages = Seq(normalize, reduceDim).flatten
    val pipeline = new Pipeline().setStages(stages.toArray)
    val dataFrame = pipeline.fit(df).transform(df)

    val cachedDf = dataFrame.cache()

    val (model, predictions) = fit(trainer, cachedDf)
    predictions.cache()

    import sparkApp.session.implicits._

    def extractClusterClasses(columnName: String): Traversable[(String, Int)] =
      predictions.select(idColumnName, columnName).map { r =>
        val id = r(0).asInstanceOf[String]
        val clazz = r(1).asInstanceOf[Int]
        (id, clazz + 1)
      }.collect

    def extractClusterClasssedFromProbabilities(columnName: String): Traversable[(String, Int)] =
      predictions.select(idColumnName, columnName).map { r =>
        val id = r(0).asInstanceOf[String]
        val clazz = r(1).asInstanceOf[DenseVector].values.zipWithIndex.maxBy(_._1)._2
        (id, clazz + 1)
      }.collect

    val result = model match {
      case _: KMeansModel =>
        extractClusterClasses("prediction")

      case _: LDAModel =>
        extractClusterClasssedFromProbabilities("topicDistribution")

      case _: BisectingKMeansModel =>
        extractClusterClasses("prediction")

      case _: GaussianMixtureModel =>
        extractClusterClasssedFromProbabilities("probability")
    }

    predictions.unpersist()
    cachedDf.unpersist

    (dataFrame, result)
  }

  override def pcaComponents(
    k: Int)(
    df: DataFrame
  ): DataFrame =
    InPlacePCA(k).fit(df).transform(df)

  private def train[M <: Model[M]](
    estimator: Estimator[M],
    trainingDf: DataFrame,
    testDfs: Seq[DataFrame]
  ): (DataFrame, Seq[DataFrame]) = {
    // fit the model
    val lrModel = estimator.fit(trainingDf)

    // get the predictions for the training and test data sets

    val trainPredictions = lrModel.transform(trainingDf)
    val testPredictions = testDfs.map(lrModel.transform)

    (trainPredictions, testPredictions)
  }

  private def evaluate[Q](
    evaluatorWrappers: Traversable[EvaluatorWrapper[Q]],
    trainPredictions: DataFrame,
    testPredictions: Seq[DataFrame]
  ): Traversable[(Q, Double, Seq[Double])] =
    evaluatorWrappers.flatMap { case EvaluatorWrapper(metric, evaluator) =>
      try {
        val trainValue = evaluator.evaluate(trainPredictions)
        val testValues = testPredictions.map(evaluator.evaluate)
        Some((metric, trainValue, testValues))
      } catch {
        case e: Exception =>
          val fieldNamesString = trainPredictions.schema.fieldNames.mkString(", ") + "\n"
          val rowsString = trainPredictions.take(10).map(_.toSeq.mkString(", ")).mkString("\n")

          logger.error(
            s"Evaluation of metric '$metric' failed." +
            s"Train Predictions: ${fieldNamesString + rowsString}"
          )
          None
      }
    }

  private def verifyRocAndPrResults(predictionDf: DataFrame) = {
    val probabilityMetrics = binaryMetrics(predictionDf, None, "probability")
    val rawPredictionMetrics = binaryMetrics(predictionDf, None, "rawPrediction")

    if (probabilityMetrics.isDefined && rawPredictionMetrics.isDefined) {
      def areMoreLessEqual(val1: Double, val2: Double): Boolean =
        ((val1 == 0 && val2 == 0) || (val2 != 0 && Math.abs((val1 - val2) / val2) < 0.001))

      if (!areMoreLessEqual(probabilityMetrics.get.areaUnderROC(), rawPredictionMetrics.get.areaUnderROC()))
        throw new AdaException("ROC values do not match: " + probabilityMetrics.get.areaUnderROC() + " vs " + rawPredictionMetrics.get.areaUnderROC())
      if (!areMoreLessEqual(probabilityMetrics.get.areaUnderPR(), rawPredictionMetrics.get.areaUnderPR()))
        throw new AdaException("PR values do not match: " + probabilityMetrics.get.areaUnderPR() + " vs " + rawPredictionMetrics.get.areaUnderPR())
    }
  }

  private def binaryMetricsCurves(
    predictions: DataFrame,
    numBins: Option[Int] = None
  ) =
    binaryMetrics(predictions, numBins).map { metrics =>
      BinaryClassificationCurves(
        metrics.roc().collect(),
        metrics.pr().collect(),
        metrics.fMeasureByThreshold().collect(),
        metrics.precisionByThreshold().collect(),
        metrics.recallByThreshold().collect()
      )
    }

  private def binaryMetrics(
    predictions: DataFrame,
    numBins: Option[Int] = None,
    probabilityCol: String = binaryClassifierInputName,
    labelCol: String = "label"
  ): Option[BinaryClassificationMetrics] = {
    val topRow = predictions.select(binaryClassifierInputName).head()
    if (topRow.getAs[Vector](0).size == 2) {
      val metrics = new BinaryClassificationMetrics(
        predictions.select(col(probabilityCol), col(labelCol).cast(DoubleType)).rdd.map {
          case Row(score: Vector, label: Double) => (score(1), label)
        }, numBins.getOrElse(0)
      )
      Some(metrics)
    } else
      None
  }

  private def trainWithCrossValidation[M <: Model[M]](
    trainer: Estimator[M],
    paramMaps: Array[ParamMap],
    crossValidationEvaluator: Evaluator,
    folds: Option[Int],
    trainingDf: DataFrame,
    testDfs: Seq[DataFrame]
  ): (DataFrame, Seq[DataFrame]) = {
    def trainAux[MM <: Model[MM]](estimator: Estimator[MM]) =
      train(estimator, trainingDf, testDfs)

    // use cross-validation if the folds specified together with params to search through, and train
    folds.map { folds =>

      val cv = new CrossValidator()
        .setEstimator(trainer)
        .setEstimatorParamMaps(paramMaps)
        .setEvaluator(crossValidationEvaluator)
        .setNumFolds(folds)

      trainAux(cv)
    }.getOrElse(
      trainAux(trainer)
    )
  }

  case class EvaluatorWrapper[Q](metric: Q, evaluator: Evaluator)

  private def fit[M <: Model[M]](
    estimator: Estimator[M],
    data: DataFrame
  ): (M, DataFrame) = {
    // Fit the model
    val lrModel = estimator.fit(data)

    // Make predictions.
    val predictions = lrModel.transform(data)

    (lrModel, predictions)
  }
}

abstract class Performance[T <: Enumeration#Value] {
  def evalMetric: T
  def trainingTestReplicationResults: Traversable[(Double, Double, Option[Double])]
}

case class ClassificationPerformance (
  val evalMetric: ClassificationEvalMetric.Value,
  val trainingTestReplicationResults: Traversable[(Double, Double, Option[Double])]
) extends Performance[ClassificationEvalMetric.Value]

case class RegressionPerformance(
  val evalMetric: RegressionEvalMetric.Value,
  val trainingTestReplicationResults: Traversable[(Double, Double, Option[Double])]
) extends Performance[RegressionEvalMetric.Value]

object MachineLearningUtil {

  def calcMetricStats[T <: Enumeration#Value](results: Traversable[Performance[T]]): Map[T, (MetricStatsValues, MetricStatsValues, Option[MetricStatsValues])] =
    results.map { result =>
      val trainingStats = new SummaryStatistics
      val testStats = new SummaryStatistics
      val replicationStats = new SummaryStatistics

      result.trainingTestReplicationResults.foreach { case (trainValue, testValue, replicationValue) =>
        trainingStats.addValue(trainValue)
        testStats.addValue(testValue)
        if (replicationValue.isDefined)
          replicationStats.addValue(replicationValue.get)
      }

      val sortedTrainValues = result.trainingTestReplicationResults.map(_._1).toSeq.sorted
      val sortedTestValues = result.trainingTestReplicationResults.map(_._2).toSeq.sorted
      val sortedReplicationValues = result.trainingTestReplicationResults.flatMap(_._3).toSeq.sorted

      (result.evalMetric, (
        toStats(trainingStats, median(sortedTrainValues)),
        toStats(testStats, median(sortedTestValues)),
        if (replicationStats.getN > 0) Some(toStats(replicationStats, median(sortedReplicationValues))) else None
      ))
    }.toMap

  def median(seq: Seq[Double]): Double = {
    val middle = seq.size / 2
    if (seq.size % 2 == 1)
      seq(middle)
    else {
      val med1 = seq(middle - 1)
      val med2 = seq(middle)
      (med1 + med2) /2
    }
  }

  def toStats(summaryStatistics: SummaryStatistics, median: Double) =
    MetricStatsValues(summaryStatistics.getMean, summaryStatistics.getMin, summaryStatistics.getMax, summaryStatistics.getVariance, Some(median))

  def createClassificationResult(
    setting: ClassificationSetting,
    results: Traversable[ClassificationPerformance],
    binCurves: Traversable[STuple3[Option[BinaryClassificationCurves]]]
  ): ClassificationResult =
    createClassificationResult(
      setting,
      calcMetricStats(results),
      binCurves
    )

  def createClassificationResult(
    setting: ClassificationSetting,
    evalMetricStatsMap: Map[ClassificationEvalMetric.Value, (MetricStatsValues, MetricStatsValues, Option[MetricStatsValues])],
    binCurves: Traversable[STuple3[Option[BinaryClassificationCurves]]]
  ): ClassificationResult = {
    // helper functions
    def trainingStatsOptional(metric: ClassificationEvalMetric.Value) =
      evalMetricStatsMap.get(metric).map(_._1)

    def testStatsOptional(metric: ClassificationEvalMetric.Value) =
      evalMetricStatsMap.get(metric).map(_._2)

    def replicationStatsOptional(metric: ClassificationEvalMetric.Value) =
      evalMetricStatsMap.get(metric).flatMap(_._3)

    def trainingStats(metric: ClassificationEvalMetric.Value) =
      trainingStatsOptional(metric).getOrElse(
        throw new AdaException(s"Classification training stats for metrics '${metric.toString}' not found.")
      )

    def testStats(metric: ClassificationEvalMetric.Value) =
      testStatsOptional(metric).getOrElse(
        throw new AdaException(s"Classification test stats for metrics '${metric.toString}' not found.")
      )

    def replicationStats(metric: ClassificationEvalMetric.Value) =
      replicationStatsOptional(metric).getOrElse(
        throw new AdaException(s"Classification replication stats for metrics '${metric.toString}' not found.")
      )

    import ClassificationEvalMetric._

    val trainingMetricStats = ClassificationMetricStats(
      f1 = trainingStats(f1),
      weightedPrecision = trainingStats(weightedPrecision),
      weightedRecall = trainingStats(weightedRecall),
      accuracy = trainingStats(accuracy),
      areaUnderROC = trainingStatsOptional(areaUnderROC),
      areaUnderPR = trainingStatsOptional(areaUnderPR)
    )

    val testMetricStats = ClassificationMetricStats(
      f1 = testStats(f1),
      weightedPrecision = testStats(weightedPrecision),
      weightedRecall = testStats(weightedRecall),
      accuracy = testStats(accuracy),
      areaUnderROC = testStatsOptional(areaUnderROC),
      areaUnderPR = testStatsOptional(areaUnderPR)
    )

    val replicationMetricStats =
      // we assume if accuracy is defined the rest is fine, otherwise nothing is defined
      if (replicationStatsOptional(accuracy).isDefined)
        Some(
          ClassificationMetricStats(
            f1 = replicationStats(f1),
            weightedPrecision = replicationStats(weightedPrecision),
            weightedRecall = replicationStats(weightedRecall),
            accuracy = replicationStats(accuracy),
            areaUnderROC = replicationStatsOptional(areaUnderROC),
            areaUnderPR = replicationStatsOptional(areaUnderPR)
          )
        )
      else
        None

    val binCurvesSeq = binCurves.toSeq

    ClassificationResult(
      None,
      setting.copy(inputFieldNames = setting.inputFieldNames.sorted),
      trainingMetricStats,
      testMetricStats,
      replicationMetricStats,
      binCurvesSeq.flatMap(_._1),
      binCurvesSeq.flatMap(_._2),
      binCurvesSeq.flatMap(_._3)
    )
  }

  def createRegressionResult(
    setting: RegressionSetting,
    results: Traversable[RegressionPerformance]
  ): RegressionResult =
    createRegressionResult(
      setting,
      calcMetricStats(results)
    )

  def createRegressionResult(
    setting: RegressionSetting,
    evalMetricStatsMap: Map[RegressionEvalMetric.Value, (MetricStatsValues, MetricStatsValues, Option[MetricStatsValues])]
  ): RegressionResult = {
    // helper functions
    def trainingStatsOptional(metric: RegressionEvalMetric.Value) =
      evalMetricStatsMap.get(metric).map(_._1)

    def testStatsOptional(metric: RegressionEvalMetric.Value) =
      evalMetricStatsMap.get(metric).map(_._2)

    def replicationStatsOptional(metric: RegressionEvalMetric.Value) =
      evalMetricStatsMap.get(metric).flatMap(_._3)

    def trainingStats(metric: RegressionEvalMetric.Value) =
      trainingStatsOptional(metric).getOrElse(
        throw new AdaException(s"Regression training stats for metrics '${metric.toString}' not found.")
      )

    def testStats(metric: RegressionEvalMetric.Value) =
      testStatsOptional(metric).getOrElse(
        throw new AdaException(s"Regression test stats for metrics '${metric.toString}' not found.")
      )

    def replicationStats(metric: RegressionEvalMetric.Value) =
      replicationStatsOptional(metric).getOrElse(
        throw new AdaException(s"Regression replication stats for metrics '${metric.toString}' not found.")
      )

    import RegressionEvalMetric._

    val trainingMetricStats = RegressionMetricStats(
      mse = trainingStats(mse),
      rmse = trainingStats(rmse),
      r2 = trainingStats(r2),
      mae = trainingStats(mae)
    )

    val testMetricStats = RegressionMetricStats(
      mse = testStats(mse),
      rmse = testStats(rmse),
      r2 = testStats(r2),
      mae = testStats(mae)
    )

    val replicationMetricStats =
      // we assume if mse is defined the rest is fine, otherwise nothing is defined
      if (replicationStatsOptional(mse).isDefined)
        Some(
          RegressionMetricStats(
            mse = replicationStats(mse),
            rmse = replicationStats(rmse),
            r2 = replicationStats(r2),
            mae = replicationStats(mae)
          )
        )
      else
        None

    RegressionResult(
      None,
      setting.copy(inputFieldNames = setting.inputFieldNames.sorted),
      trainingMetricStats,
      testMetricStats,
      replicationMetricStats
    )
  }

  //  override def selectFeaturesAsChiSquare(
  //    data: DataFrame,
  //    featuresToSelectNum: Int
  //  ): DataFrame = {
  //    val model = selectFeaturesAsChiSquareModel(data, featuresToSelectNum)
  //
  //    model.transform(data)
  //  }

  //  override def selectFeaturesAsChiSquare(
  //    data: Traversable[JsObject],
  //    inputAndOutputFields: Seq[Field],
  //    outputFieldName: String,
  //    featuresToSelectNum: Int,
  //    discretizerBucketsNum: Int
  //  ): Traversable[String] = {
  //    val fieldNameSpecs = inputAndOutputFields.map(field => (field.name, field.fieldTypeSpec))
  //    val df = FeaturesDataFrameFactory(session, data, fieldNameSpecs, Some(outputFieldName), Some(discretizerBucketsNum))
  //    val inputDf = BooleanLabelIndexer().transform(df)
  //
  //    // get the Chi-Square model
  //    val model = selectFeaturesAsChiSquareModel(inputDf, featuresToSelectNum)
  //
  //    // extract the features
  //    val featureNames = inputDf.columns.filterNot(columnName => columnName.equals("features") || columnName.equals("label"))
  //    model.selectedFeatures.map(featureNames(_))
  //  }
  //
  //  private def selectFeaturesAsChiSquareModel(
  //    data: DataFrame,
  //    featuresToSelectNum: Int
  //  ) = {
  //    val selector = new ChiSqSelector()
  //      .setFeaturesCol("features")
  //      .setLabelCol("label")
  //      .setOutputCol("selectedFeatures")
  //      .setNumTopFeatures(featuresToSelectNum)
  //
  //    selector.fit(data)
  //  }
}

case class ClassificationResultsAuxHolder(
  evalResults: Traversable[(ClassificationEvalMetric.Value, Double, Seq[Double])],
  count: Long,
  binTrainingCurves: Option[BinaryClassificationCurves],
  binTestCurves: Seq[Option[BinaryClassificationCurves]]
)

case class RegressionResultsAuxHolder(
  evalResults: Traversable[(RegressionEvalMetric.Value, Double, Seq[Double])],
  count: Long,
  expectedAndActualOutputs: Traversable[Seq[(Double, Double)]]
)

case class ClassificationResultsHolder(
  performanceResults: Traversable[ClassificationPerformance],
  counts: Traversable[Long],
  binCurves: Traversable[STuple3[Option[BinaryClassificationCurves]]]
)

case class RegressionResultsHolder(
  performanceResults: Traversable[RegressionPerformance],
  counts: Traversable[Long],
  expectedAndActualOutputs: Traversable[Traversable[Seq[(Double, Double)]]]
)