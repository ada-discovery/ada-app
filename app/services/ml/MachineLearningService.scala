package services.ml

import java.util.UUID
import javax.inject.{Inject, Singleton}
import java.{lang => jl, util => ju}

import com.google.inject.ImplementedBy
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{AdaException, Field, FieldTypeId, FieldTypeSpec}
import models.ml.classification.Classification
import models.ml.regression.Regression
import models.ml.unsupervised.UnsupervisedLearning
import models.ml._
import util.{GroupMapList, STuple3}
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, Evaluator, MulticlassClassificationEvaluator, RegressionEvaluator}
import org.apache.spark.ml.feature._
import org.apache.spark.ml.{Estimator, Model, Pipeline, Transformer}
import org.apache.spark.sql.types.{Metadata, MetadataBuilder, StructType, _}
import org.apache.spark.sql.{DataFrame, Dataset, Row}
import org.apache.spark.ml.clustering._
import org.apache.spark.ml.linalg.{DenseVector, Vector, Vectors}
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions.col
import play.api.libs.json.{JsObject, Json}
import services.{FeaturesDataFrameFactory, SparkApp}
import play.api.{Configuration, Logger}

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
    setting: LearningSetting = LearningSetting(),
    replicationData: Traversable[JsObject] = Nil,
    binCurvesNumBins: Option[Int] = None
  ): Future[ClassificationResultsHolder]

  def regress(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Regression,
    setting: LearningSetting = LearningSetting()
  ): Future[Traversable[RegressionPerformance]]

  def cluster[M <: Model[M]](
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int] = None
  ): Traversable[(String, Int)]

  def clusterAndGetPCA12[M <: Model[M]](
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

  def selectFeaturesAsChiSquare(
    data: DataFrame,
    featuresToSelectNum: Int
  ): DataFrame

  def selectFeaturesAsChiSquare(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    featuresToSelectNum: Int,
    discretizerBucketsNum: Int
  ): Traversable[String]

  // AFTSurvivalRegression
  // IsotonicRegression
}

@Singleton
private class MachineLearningServiceImpl @Inject() (
    sparkApp: SparkApp,
    configuration: Configuration
  ) extends MachineLearningService {

  private val logger = Logger // (this.getClass())

  private val session = sparkApp.session
  private implicit val sqlContext = sparkApp.sqlContext

  private val defaultTrainingTestingSplit = 0.8
  private val repetitionParallelism = configuration.getInt("ml.repetition_parallelism").getOrElse(2)
  private val binaryClassifierInputName = configuration.getString("ml.binary_classifier.input").getOrElse("probability")
  private val binaryPredictionVectorizer = new IndexVectorizer() { setInputCol("prediction"); setOutputCol(binaryClassifierInputName)}

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
        evaluator,
        {
          dataset: Dataset[_] =>
          val topRow = dataset.select(binaryClassifierInputName).head()
          topRow.getAs[Vector](0).size == 2
        }
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
    setting: LearningSetting,
    replicationData: Traversable[JsObject],
    binCurvesNumBins: Option[Int]
  ): Future[ClassificationResultsHolder] = {
    val trainer = SparkMLEstimatorFactory(mlModel)

    // TRAINING AND TEST DATA

    // create a data frame with all the features
    val df = FeaturesDataFrameFactory(session, data, fields, Some(outputFieldName))
    df.cache

    // transform the df to classification one
    val finalDf = transformToClassificationDataFrame(df, setting)
    finalDf.cache


    // REPLICATION DATA (if any)

    // create a data frame with all the features
    val replicationDf =
      if (replicationData.nonEmpty) {
        val df = FeaturesDataFrameFactory(session, replicationData, fields, Some(outputFieldName))
        Some(df.cache)
      } else
        None

    // transform the df to classification one
    val finalReplicationDf = replicationDf.map { replicationDf =>
      val df = transformToClassificationDataFrame(replicationDf, setting)
      df.cache
    }


    // REPEAT THE TRAINING-TEST CYCLE

    val samplingNeeded = setting.samplingRatios.nonEmpty

    import sparkApp.sqlContext.implicits._

    val labelStrings: Traversable[String] =
      if (samplingNeeded)
        finalDf.select("labelString").distinct().map(_.getString(0)).collect()
      else
        Nil

    // split the data into training and test parts
    val split = setting.trainingTestingSplit.getOrElse(defaultTrainingTestingSplit)

    // evaluators
    val evaluators = classificationEvaluators ++ binClassificationEvaluators

    val resultsWithCountsFuture = util.parallelize(1 to setting.repetitions.getOrElse(1), repetitionParallelism) { index =>
      logger.info(s"Execution of repetition $index started.")

      // sampling
      val sampledDf =
        if (samplingNeeded)
          sample(setting.samplingRatios, labelStrings)(finalDf)
        else
          finalDf
      val count = sampledDf.count()

      logger.info("Total count : " + count)

      val Array(training, test) = sampledDf.randomSplit(Array(split, 1 - split))

      // run the trainer (with folds) on the given training and test data sets (replication df is treated as "another" test data set if provided)

      val testSets = Seq(Some(test.cache), finalReplicationDf).flatten
      val (trainingPredictions, testPredictions) = trainWithCrossValidation(trainer, evaluators.head.evaluator, setting.crossValidationFolds, training.cache, testSets)

      // evaluate the performance

      def withBinaryEvaluationCol(df: DataFrame) =
        if (df.columns.contains(binaryClassifierInputName))
          df
        else
          binaryPredictionVectorizer.transform(df)

      val trainingPredictionsExt = withBinaryEvaluationCol(trainingPredictions)
      val testPredictionsExt = testPredictions.map(withBinaryEvaluationCol)
      val results = evaluate(evaluators, trainingPredictionsExt, testPredictionsExt)

      val binTrainingCurves = binaryMetricsCurves(trainingPredictions, binCurvesNumBins)
      val binTestCurves = testPredictions.map(binaryMetricsCurves(_, binCurvesNumBins))

      // unpersist and return the results

      training.unpersist
      test.unpersist

      ClassificationResultsAuxHolder(results, count, binTrainingCurves, binTestCurves)
    }


    // EVALUATE PERFORMANCE

    resultsWithCountsFuture.map { resultHolders =>
      // uncache
      finalDf.unpersist
      df.unpersist
      if (finalReplicationDf.isDefined)
        finalReplicationDf.get.unpersist
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

  case class ClassificationResultsAuxHolder(
    evalResults: Traversable[(ClassificationEvalMetric.Value, Double, Seq[Double])],
    count: Long,
    binTrainingCurves: Option[BinaryClassificationCurves],
    binTestCurves: Seq[Option[BinaryClassificationCurves]]
  )

  private def transformToClassificationDataFrame(
    df: DataFrame,
    setting: LearningSetting
  ): DataFrame = {
    // normalize the features
    val normalizeFeatures = new SchemaUnchangedTransformer(normalizeFeaturesOptional(setting.featuresNormalizationType))

    // reduce the dimensionality if needed
    val reduceDim = new SchemaUnchangedTransformer(pcaComponentsOptional(setting.pcaDims))

    // make sure the output is string
    val makeIndexBooleanLabel = BooleanLabelIndexer

    // keep the label as string for sampling (if needed)
    val keepLabelString = new IndexToString()
      .setInputCol("label")
      .setOutputCol("labelString")

    // create the stages and run a pipeline
    val preStages = Seq(normalizeFeatures, reduceDim, makeIndexBooleanLabel)
    val stages = if (setting.samplingRatios.nonEmpty) preStages ++ Seq(keepLabelString) else preStages
    val pipeline = new Pipeline().setStages(stages.toArray)
    pipeline.fit(df).transform(df)
  }

  private def sample(
    samplingRatios: Seq[(String, Double)],
    labelStrings: Traversable[String])(
    df: DataFrame
  ): DataFrame = {
    import sparkApp.sqlContext.implicits._

    val labelSamplingRatioMap = samplingRatios.toMap

    val sampledDfs = labelStrings.map { label =>
      val pdf = df.filter($"labelString" === label)

      labelSamplingRatioMap.get(label).map { samplingRatio =>
        val newPdf = pdf.sample(false, samplingRatio)
        logger.info(label + ": " + pdf.count() + " -> " + newPdf.count())
        newPdf
      }.getOrElse(pdf)
    }

    if (sampledDfs.nonEmpty)
      sampledDfs.tail.foldLeft(sampledDfs.head)(_.union(_))
    else
      df
  }

  override def regress(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Regression,
    setting: LearningSetting
  ): Future[Traversable[RegressionPerformance]] = {
    val trainer = SparkMLEstimatorFactory(mlModel)

    val df = FeaturesDataFrameFactory(session, data, fields, Some(outputFieldName))

    // normalize the features
    val normalizeFeatures = new SchemaUnchangedTransformer(normalizeFeaturesOptional(setting.featuresNormalizationType))

    // reduce the dimensionality if needed
    val reduceDim = new SchemaUnchangedTransformer(pcaComponentsOptional(setting.pcaDims))

    // execute the pipeline
    val pipeline = new Pipeline().setStages(Array(normalizeFeatures, reduceDim))
    val dataFrame = pipeline.fit(df).transform(df)

    dataFrame.cache()

    // split the data into training and test parts
    val split = setting.trainingTestingSplit.getOrElse(defaultTrainingTestingSplit)

    val resultsFuture = util.parallelize(1 to setting.repetitions.getOrElse(1), repetitionParallelism) { index =>
      println(s"Execution of repetition $index started.")
      val Array(training, test) = dataFrame.randomSplit(Array(split, 1 - split))

      // run the trainer (with folds) on the given training and test data sets
      val (trainPredictions, testPredictions) = trainWithCrossValidation(trainer, regressionEvaluators.head.evaluator, setting.crossValidationFolds, training.cache, Seq(test.cache))

      // evaluate the performance
      val results = evaluate(regressionEvaluators, trainPredictions, testPredictions)

      training.unpersist
      test.unpersist
      results
    }

    resultsFuture.map { results =>
      // uncache
      dataFrame.unpersist()

      // create performance results
      results.flatten.groupBy(_._1).map { case (evalMetric, results) =>
        RegressionPerformance(evalMetric, results.map( x => (x._2, x._3.head)))
      }
    }
  }

  override def cluster[M <: Model[M]](
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int]
  ): Traversable[(String, Int)] = {
    val (df, idClusters) = clusterAux(data, fields, mlModel, featuresNormalizationType, pcaDim)
    idClusters
  }

  override def clusterAndGetPCA12[M <: Model[M]](
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int]
  ): (Traversable[(String, Int)], Traversable[(String, (Double, Double))]) = {
    val (df, idClusters) = clusterAux(data, fields, mlModel, featuresNormalizationType, pcaDim)

    // reduce the dimensionality if needed
    val pca12Df = df.transform(pcaComponents(2))

    import sparkApp.session.implicits._

    val idPca12Values = pca12Df.select(JsObjectIdentity.name, "features").map { r =>
      val id = r(0).asInstanceOf[String]
      val values = r(1).asInstanceOf[DenseVector].values
      (id, (values(0), values(1)))
    }.collect

    (idClusters, idPca12Values)
  }

  private def clusterAux[M <: Model[M]](
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorTransformType.Value],
    pcaDim: Option[Int]
  ): (DataFrame, Traversable[(String, Int)]) = {
    val trainer = SparkMLEstimatorFactory[M](mlModel)

    // prepare a data frame for learning
    val featureFieldNames = fields.map(_._1)
    val fieldsWithId = fields ++ Seq((JsObjectIdentity.name, FieldTypeSpec(FieldTypeId.String)))
    val df = FeaturesDataFrameFactory(session, data, fieldsWithId, featureFieldNames)

    val normalizedDf = normalizeFeaturesOptional(featuresNormalizationType)(df)

    // reduce the dimensionality if needed
    val dataFrame = normalizedDf.transform(pcaComponentsOptional(pcaDim))

    val cachedDf = dataFrame.cache()

    val (model, predictions) = fit(trainer, cachedDf)

    import sparkApp.session.implicits._

    def extractClusterClasses(columnName: String): Traversable[(String, Int)] =
      predictions.select(JsObjectIdentity.name, columnName).map { r =>
        val id = r(0).asInstanceOf[String]
        val clazz = r(1).asInstanceOf[Int]
        (id, clazz + 1)
      }.collect

    def extractClusterClasssedFromProbabilities(columnName: String): Traversable[(String, Int)] =
      predictions.select(JsObjectIdentity.name, columnName).map { r =>
        val id = r(0).asInstanceOf[String]
        val clazz = r(1).asInstanceOf[DenseVector].values.zipWithIndex.maxBy(_._1)._2
        (id, clazz + 1)
      }.collect

    val result = model match {
      case m: KMeansModel =>
        // Evaluate clustering by computing Within Set Sum of Squared Errors.
        //        val WSSSE = m.computeCost(cachedDf)
        //        println(s"Within Set Sum of Squared Errors = $WSSSE")

        //        // Shows the result.
        //        println("Cluster Centers:")
        //        m.clusterCenters.foreach(println)

        // extract cluster classes
        extractClusterClasses("prediction")

      case m: LDAModel =>
        //        val ll = m.logLikelihood(cachedDf)
        //        val lp = m.logPerplexity(cachedDf)
        //        println(s"The lower bound on the log likelihood of the entire corpus: $ll")
        //        println(s"The upper bound bound on perplexity: $lp")
        //
        //        // Describe topics.
        //        val topics = m.describeTopics(3)
        //        println("The topics described by their top-weighted terms:")
        //        topics.show(false)

        //        // Shows the result.
        //        val transformed = model.transform(cachedDf)
        //        transformed.show(false)

        // extract cluster classes
        extractClusterClasssedFromProbabilities("topicDistribution")

      case m: BisectingKMeansModel =>
        // Evaluate clustering.
        //        val cost = m.computeCost(cachedDf)
        //        println(s"Within Set Sum of Squared Errors = $cost")
        //
        //        // Shows the result.
        //        println("Cluster Centers: ")
        //        val centers = m.clusterCenters
        //        centers.foreach(println)

        // extract cluster classes
        extractClusterClasses("prediction")

      case m: GaussianMixtureModel =>
        // output parameters of mixture model model
        //        for (i <- 0 until m.getK) {
        //          println(s"Gaussian $i:\nweight=${m.weights(i)}\n" +
        //            s"mu=${m.gaussians(i).mean}\nsigma=\n${m.gaussians(i).cov}\n")
        //        }

        // extract cluster classes
        extractClusterClasssedFromProbabilities("probability")
    }

    cachedDf.unpersist

    (normalizedDf, result)
  }

  private def pcaComponentsOptional(
    k: Option[Int])(
    df: DataFrame
  ) =
    k.map( pcaDims =>
      df.transform(pcaComponents(pcaDims))
    ).getOrElse(df)

  override def pcaComponents(
    k: Int)(
    df: DataFrame
  ): DataFrame = {
    val pca = new PCA()
      .setInputCol("features")
      .setOutputCol("pcaFeatures")
      .setK(k)
      .fit(df)

    PCAModel
    // replace in-place
    pca.transform(df).drop("features").withColumnRenamed("pcaFeatures", "features")
  }

  private def normalizeFeaturesOptional(
    featuresNormalizationType: Option[VectorTransformType.Value])(
    df: DataFrame
  ): DataFrame =
    if (featuresNormalizationType.isDefined) {
      val featureTransformer = new SchemaUnchangedTransformer(
        FeatureTransformer(session)(_, featuresNormalizationType.get)
      )

      // replace in-place
      featureTransformer.transform(df).drop("features").withColumnRenamed("scaledFeatures", "features")
    } else
      df

  private def train[M <: Model[M]](
    estimator: Estimator[M],
    trainingDf: DataFrame,
    testDfs: Seq[DataFrame]
  ): (DataFrame, Seq[DataFrame]) = {
    // fit the model
    val lrModel = estimator.fit(trainingDf)

    // get the predictions for the training and test data sets
    def getPredictions(df: DataFrame) = lrModel.transform(df)

    val trainPredictions = getPredictions(trainingDf)
    val testPredictions = testDfs.map(getPredictions)
    (trainPredictions, testPredictions)
  }

  private def evaluate[Q](
    evaluatorWrappers: Traversable[EvaluatorWrapper[Q]],
    trainPredictions: DataFrame,
    testPredictions: Seq[DataFrame]
  ): Traversable[(Q, Double, Seq[Double])] =
    evaluatorWrappers.flatMap { case EvaluatorWrapper(metric, evaluator, isApplicable) =>
      if (isApplicable(trainPredictions) && testPredictions.forall(isApplicable)) {
        try {
          val trainValue = evaluator.evaluate(trainPredictions)
          val testValues = testPredictions.map(evaluator.evaluate)
          Some((metric, trainValue, testValues))
        } catch {
          case e: Exception =>
            logger.error(s"Evaluation of metric '$metric' failed."); None
        }
      } else
        None
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
    crossValidationEvaluator: Evaluator,
    folds: Option[Int],
    trainingDf: DataFrame,
    testDfs: Seq[DataFrame]
  ): (DataFrame, Seq[DataFrame]) = {
    def trainAux[MM <: Model[MM]](estimator: Estimator[MM]) =
      train(estimator, trainingDf, testDfs)

    // TODO: since we are not selecting a model cross validation here is useless
    // use cross-validation if the folds specified (without parameter optimization) and train
    folds.map { folds =>
//      val pipeline = new Pipeline().setStages(Array(trainer))
      val paramGrid = new ParamGridBuilder().build() // No parameter search

      val cv = new CrossValidator()
        .setEstimator(trainer)
        .setEstimatorParamMaps(paramGrid)
        .setEvaluator(crossValidationEvaluator)
        .setNumFolds(folds)

      trainAux(cv)
    }.getOrElse(
      trainAux(trainer)
    )
  }

  case class EvaluatorWrapper[Q](
    metric: Q,
    evaluator: Evaluator,
    isApplicable: Dataset[_] => Boolean = _ => true
  )

  private def fit[M <: Model[M], Q](
    estimator: Estimator[M],
    data: DataFrame
  ): (M, DataFrame) = {
    // Fit the model
    val lrModel = estimator.fit(data)

    // Make predictions.
    val predictions = lrModel.transform(data)

    (lrModel, predictions)
  }

  override def selectFeaturesAsChiSquare(
    data: DataFrame,
    featuresToSelectNum: Int
  ): DataFrame = {
    val model = selectFeaturesAsChiSquareModel(data, featuresToSelectNum)

    model.transform(data)
  }

  override def selectFeaturesAsChiSquare(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    featuresToSelectNum: Int,
    discretizerBucketsNum: Int
  ): Traversable[String] = {
    val df = FeaturesDataFrameFactory(session, data, fields, Some(outputFieldName), Some(discretizerBucketsNum))
    val inputDf = BooleanLabelIndexer.transform(df)

    // get the Chi-Square model
    val model = selectFeaturesAsChiSquareModel(inputDf, featuresToSelectNum)

    // extract the features
    val featureNames = inputDf.columns.filterNot(columnName => columnName.equals("features") || columnName.equals("label"))
    model.selectedFeatures.map(featureNames(_))
  }

  private def selectFeaturesAsChiSquareModel(
    data: DataFrame,
    featuresToSelectNum: Int
  ) = {
    val selector = new ChiSqSelector()
      .setFeaturesCol("features")
      .setLabelCol("label")
      .setOutputCol("selectedFeatures")
      .setNumTopFeatures(featuresToSelectNum)

    selector.fit(data)
  }
}

object ClassificationEvalMetric extends Enumeration {
  val f1, weightedPrecision, weightedRecall, accuracy, areaUnderROC, areaUnderPR = Value
}

object RegressionEvalMetric extends Enumeration {
  val mse, rmse, r2, mae = Value
}

abstract class Performance {
  type T <: Enumeration#Value
  def evalMetric: T
  def trainingTestResults: Traversable[(Double, Double)]
}

case class ClassificationPerformance (
  val evalMetric: ClassificationEvalMetric.Value,
  val trainingTestReplicationResults: Traversable[(Double, Double, Option[Double])]
) extends Performance {
  override type T = ClassificationEvalMetric.Value

  override def trainingTestResults =
    trainingTestReplicationResults.map { case (train, test, _) => (train, test) }
}

case class RegressionPerformance(
  val evalMetric: RegressionEvalMetric.Value,
  val trainingTestResults: Traversable[(Double, Double)]
) extends Performance {
  override type T = RegressionEvalMetric.Value
}

object MachineLearningUtil {

  def calcClassificationMetricStats(results: Traversable[ClassificationPerformance]) = {
    def toStats(summaryStatistics: SummaryStatistics) =
      MetricStatsValues(summaryStatistics.getMean, summaryStatistics.getMin, summaryStatistics.getMax, summaryStatistics.getVariance)

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

      (result.evalMetric, (
        toStats(trainingStats),
        toStats(testStats),
        if (replicationStats.getN > 0) Some(toStats(replicationStats)) else None
      ))
    }.toMap
  }

  def createClassificationResult(
    results: Traversable[ClassificationPerformance],
    setting: ClassificationSetting
  ): ClassificationResult =
    createClassificationResult(
      setting,
      calcClassificationMetricStats(results),
      Nil
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

  //  private def train(
  //    reg: LogisticRegression,
  //    trainData: DataFrame,
  //    testData: DataFrame
  //  ) = {
  //    // Fit the model
  //    val lrModel = reg.fit(trainData)
  //
  //    // Make predictions.
  //    val predictions = lrModel.transform(testData)
  //    predictions.printSchema()
  //
  //    // Select example rows to display.
  //    predictions.select("rawPrediction", "prediction", "label", "features").show(20)
  //
  //    // Select (prediction, true label) and compute test error
  //    val evaluator = new BinaryClassificationEvaluator()
  //      .setLabelCol("label")
  //      .setRawPredictionCol("rawPrediction")
  //
  //    val accuracy = evaluator.evaluate(predictions)
  //    println("Test Error = " + (1.0 - accuracy))
  //
  //    val binarySummary = lrModel.evaluate(testData).asInstanceOf[BinaryLogisticRegressionSummary]
  //
  //    val roc = binarySummary.roc
  //    println(s"areaUnderROC: ${binarySummary.areaUnderROC}")
  //
  //    // Set the model threshold to maximize F-Measure
  //    val fMeasure = binarySummary.fMeasureByThreshold
  //    val maxFMeasure = fMeasure.select(max("F-Measure")).head().getDouble(0)
  //    val bestThreshold = fMeasure.where($"F-Measure" === maxFMeasure).select("threshold").head().getDouble(0)
  //    lrModel.setThreshold(bestThreshold)
  //    println("Best threshold = " + bestThreshold)
  //
  //    val predictions2 = lrModel.transform(testData)
  //
  //    val accuracy2 = evaluator.evaluate(predictions2)
  //    println("Test Error for the est threshold = " + (1.0 - accuracy2))
  //
  ////    // Print the coefficients and intercept for logistic regression
  ////    println(s"Coefficients: ${lrModel.coefficients} Intercept: ${lrModel.intercept}")
  //  }
  //
  //  private def train(
  //    reg: RandomForestClassifier,
  //    trainData: DataFrame,
  //    testData: DataFrame
  //  ) = {
  //    // Fit the model
  //    val lrModel = reg.fit(trainData)
  //
  //    // Make predictions.
  //    val predictions = lrModel.transform(testData)
  //    predictions.printSchema()
  //
  //    // Select example rows to display.
  //    predictions.select("rawPrediction", "prediction", "label", "features").show(20)
  //
  //    // Select (prediction, true label) and compute test error
  //    val evaluator = new BinaryClassificationEvaluator()
  //      .setLabelCol("label")
  //      .setRawPredictionCol("rawPrediction")
  //
  //    val accuracy = evaluator.evaluate(predictions)
  //    println("Test Error = " + (1.0 - accuracy))
  //  }
}

case class ClassificationResultsHolder(
  performanceResults: Traversable[ClassificationPerformance],
  counts: Traversable[Long],
  binCurves: Traversable[STuple3[Option[BinaryClassificationCurves]]]
)