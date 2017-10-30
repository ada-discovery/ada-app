package services.ml

import java.util.UUID
import javax.inject.{Inject, Singleton}
import java.{lang => jl, util => ju}

import com.google.inject.ImplementedBy
import dataaccess.{AscSort, FieldType, FieldTypeHelper}
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{AdaException, FieldTypeId, FieldTypeSpec}
import models.ml.classification.Classification
import models.ml.regression.Regression
import models.ml.unsupervised.UnsupervisedLearning
import models.ml._
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import org.apache.spark.ml.attribute.NominalAttribute
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, Evaluator, MulticlassClassificationEvaluator, RegressionEvaluator}
import org.apache.spark.ml.feature._
import org.apache.spark.ml.{Estimator, Model, Pipeline, Transformer}
import org.apache.spark.sql.types.{Metadata, MetadataBuilder, StructType, _}
import org.apache.spark.sql.{DataFrame, Dataset, Row}
import org.apache.spark.ml.clustering._
import org.apache.spark.ml.linalg.{DenseVector, Vector, Vectors}
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.ml.stat.ChiSquareTest
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.rdd.RDD
import play.api.libs.json.{JsObject, Json}
import services.SparkApp
import play.api.{Configuration, Logger}

import scala.concurrent.{Await, Future}
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions._

@ImplementedBy(classOf[MachineLearningServiceImpl])
trait MachineLearningService {

  def classify(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Classification,
    setting: LearningSetting = LearningSetting()
  ): Future[Traversable[ClassificationPerformance]]

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

  def discretizeAsQuantiles(
    data: DataFrame,
    bucketsNum: Int,
    columnName: String
  ): DataFrame

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

  def independenceTest(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    targetFieldName: String
  ): Seq[(String, ChiSquareResult)]

  def independenceTest(
    df: DataFrame
  ): Seq[ChiSquareResult]

  // AFTSurvivalRegression
  // IsotonicRegression
}

@Singleton
private class MachineLearningServiceImpl @Inject() (
    sparkApp: SparkApp,
    configuration: Configuration
  ) extends MachineLearningService {

  private val logger = Logger // (this.getClass())

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val session = sparkApp.session
  private val sparkContext = sparkApp.sc
  private implicit val sqlContext = sparkApp.sqlContext

  private val defaultTrainingTestingSplit = 0.8
  private val repetitionParallelism = configuration.getInt("ml.repetition_parallelism").getOrElse(2)

  override def classify(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Classification,
    setting: LearningSetting
  ): Future[Traversable[ClassificationPerformance]] = {
    val trainer = SparkMLEstimatorFactory(mlModel)

    val evaluators = ClassificationEvalMetric.values.filter(metric =>
      metric != ClassificationEvalMetric.areaUnderPR && metric != ClassificationEvalMetric.areaUnderROC
    ).toSeq.map { metric =>
      val evaluator = new MulticlassClassificationEvaluator()
        .setLabelCol("label")
        .setPredictionCol("prediction")
        .setMetricName(metric.toString)

      (metric, evaluator)
    }

    val binEvaluators = fields.find(_._1 == outputFieldName).map { case (_, outputFieldType) =>
      if (outputFieldType.fieldType == FieldTypeId.Boolean || (outputFieldType.fieldType == FieldTypeId.Enum && outputFieldType.enumValues.get.size == 2)) {
        Seq(ClassificationEvalMetric.areaUnderPR, ClassificationEvalMetric.areaUnderROC).map { metric =>
          val evaluator = new BinaryClassificationEvaluator()
            .setLabelCol("label")
            .setRawPredictionCol("rawPrediction")
            .setMetricName(metric.toString)

          (metric, evaluator)
        }
      } else
        Nil
    }.getOrElse(Nil)

    val df = jsonsToLearningDataFrame(data, fields, Some(outputFieldName))
    df.cache

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

    val samplingNeeded = setting.samplingRatios.nonEmpty

    val stages = if (samplingNeeded) preStages ++ Seq(keepLabelString) else preStages

    val pipeline = new Pipeline().setStages(stages.toArray)

    val finalDf = pipeline.fit(df).transform(df)

    finalDf.cache

    import sparkApp.sqlContext.implicits._

    val labelStrings: Traversable[String] =
      if (samplingNeeded)
        finalDf.select("labelString").distinct().map(_.getString(0)).collect()
      else
        Nil

    // split the data into training and test parts
    val split = setting.trainingTestingSplit.getOrElse(defaultTrainingTestingSplit)

    val resultsWithCountsFuture = util.parallelize(1 to setting.repetitions.getOrElse(1), repetitionParallelism) { index =>
      logger.info(s"Execution of repetition $index started.")

      // sampling
      val sampledDf =
        if (samplingNeeded) {
          val labelSamplingRatioMap = setting.samplingRatios.toMap

          val sampledDfs = labelStrings.map { label =>
            val pdf = finalDf.filter($"labelString" === label)

            labelSamplingRatioMap.get(label).map { samplingRatio =>
              val newPdf = pdf.sample(false, samplingRatio)
              logger.info(label + ": " + pdf.count() + " -> " + newPdf.count())
              newPdf
            }.getOrElse(pdf)
          }

          if (sampledDfs.nonEmpty)
            sampledDfs.tail.foldLeft(sampledDfs.head)(_.union(_))
          else
            finalDf
        } else
        finalDf

      val count = sampledDf.count()

      logger.info("Total count : " + count)

      val Array(training, test) = sampledDf.randomSplit(Array(split, 1 - split))

        // run the trainer (with folds) on the given training and test data sets
      val results = trainWithFolds(trainer, evaluators ++ binEvaluators, setting.crossValidationFolds, training.cache, test.cache, true)
      training.unpersist
      test.unpersist
      (results, count)
    }

    resultsWithCountsFuture.map { resultsWithCounts =>
      // uncache
      finalDf.unpersist
      df.unpersist

      val results = resultsWithCounts.map(_._1)

      // create performance results
      results.flatten.groupBy(_._1).map { case (evalMetric, results) =>
        ClassificationPerformance(evalMetric, results.map( x => (x._2, x._3)))
      }
    }
  }

  override def regress(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Regression,
    setting: LearningSetting
  ): Future[Traversable[RegressionPerformance]] = {
    val trainer = SparkMLEstimatorFactory(mlModel)

    val df = jsonsToLearningDataFrame(data, fields, Some(outputFieldName))

    // normalize the features
    val normalizeFeatures = new SchemaUnchangedTransformer(normalizeFeaturesOptional(setting.featuresNormalizationType))

    // reduce the dimensionality if needed
    val reduceDim = new SchemaUnchangedTransformer(pcaComponentsOptional(setting.pcaDims))

    // execute the pipeline
    val pipeline = new Pipeline().setStages(Array(normalizeFeatures, reduceDim))
    val dataFrame = pipeline.fit(df).transform(df)

    val evaluators = RegressionEvalMetric.values.toSeq.map { metric =>

      val evaluator = new RegressionEvaluator()
        .setLabelCol("label")
        .setPredictionCol("prediction")
        .setMetricName(metric.toString)

      (metric, evaluator)
    }

    dataFrame.cache()

    // split the data into training and test parts
    val split = setting.trainingTestingSplit.getOrElse(defaultTrainingTestingSplit)

    val resultsFuture = util.parallelize(1 to setting.repetitions.getOrElse(1), repetitionParallelism) { index =>
      println(s"Execution of repetition $index started.")
      val Array(training, test) = dataFrame.randomSplit(Array(split, 1 - split))

      // run the trainer (with folds) on the given training and test data sets
      val results = trainWithFolds(trainer, evaluators, setting.crossValidationFolds, training.cache, test.cache, false)
      training.unpersist
      test.unpersist
      results
    }

    resultsFuture.map { results =>
      // uncache
      dataFrame.unpersist()

      // create performance results
      results.flatten.groupBy(_._1).map { case (evalMetric, results) =>
        RegressionPerformance(evalMetric, results.map( x => (x._2, x._3)))
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
    val df = jsonsToLearningDataFrame(data, fieldsWithId, featureFieldNames)

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
        (id, clazz)
      }.collect

    def extractClusterClasssedFromProbabilities(columnName: String): Traversable[(String, Int)] =
      predictions.select(JsObjectIdentity.name, columnName).map { r =>
        val id = r(0).asInstanceOf[String]
        val clazz = r(1).asInstanceOf[DenseVector].values.zipWithIndex.maxBy(_._1)._2
        (id, clazz)
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

  private def jsonsToLearningDataFrame(
    jsons: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    featureFieldNames: Seq[String]
  ): DataFrame = {
    // convert jsons to a data frame
    val fieldNameAndTypes = fields.map { case (name, fieldTypeSpec) => (name, ftf(fieldTypeSpec))}
    val stringFieldNames = fields.filter {_._2.fieldType == FieldTypeId.String }.map(_._1)
    val stringFieldsNotToIndex = stringFieldNames.diff(featureFieldNames).toSet
    val df = jsonsToDataFrame(jsons, fieldNameAndTypes, stringFieldsNotToIndex)

    df.transform(
      prepLearningDataFrame(featureFieldNames.toSet, None)
    )
  }

  private def jsonsToLearningDataFrame(
    jsons: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: Option[String],
    discretizerBucketNum: Option[Int] = None
  ): DataFrame = {
    // convert jsons to a data frame
    val fieldNameAndTypes = fields.map { case (name, fieldTypeSpec) => (name, ftf(fieldTypeSpec))}
    val df = jsonsToDataFrame(jsons, fieldNameAndTypes)

    // prep the data frame for learning
    val featureNames = featureFieldNames(fields, outputFieldName)

//    val numericFieldNames = df.schema.fields.flatMap { field =>
//      if (field.dataType == LongType || field.dataType == DoubleType)
//        Some(field.name)
//      else
//        None
//    }

    val numericFieldNames = fields.flatMap { case (name, fieldTypeSpec) =>
      if (featureNames.contains(name)) {
        if (fieldTypeSpec.fieldType == FieldTypeId.Integer || fieldTypeSpec.fieldType == FieldTypeId.Double)
          Some(name)
        else
          None
      } else
        None
     }

    println("Numeric field  names: " + numericFieldNames.mkString(","))

    val discretizedDf = discretizerBucketNum.map( discretizerBucketNum =>
      numericFieldNames.foldLeft(df) { case (newDf, fieldName) =>
        discretizeAsQuantiles(newDf, discretizerBucketNum, fieldName)
      }
    ).getOrElse(df)

    discretizedDf.transform(
      prepLearningDataFrame(featureNames, outputFieldName)
    )
  }

  private def featureFieldNames(
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: Option[String]
  ): Set[String] =
    outputFieldName.map( outputName =>
      fields.map(_._1).filterNot(_ == outputName)
    ).getOrElse(
      fields.map(_._1)
    ).toSet

  private def prepLearningDataFrame(
    featureFieldNames: Set[String],
    outputFieldName: Option[String])(
    df: DataFrame
  ): DataFrame = {
    //    df.printSchema()
//    df.schema.fields.foreach(field =>
//      println(s"${field.name}: ${field.dataType.typeName} (nullable = ${field.nullable}), metadata = ${field.metadata.toString()}")
//    )

    // drop null values
    val nonNullDf = df.na.drop

    val assembler = new VectorAssembler()
      .setInputCols(nonNullDf.columns.filter(featureFieldNames.contains))
      .setOutputCol("features")

    val featuresDf = assembler.transform(nonNullDf)

    outputFieldName.map(
      featuresDf.withColumnRenamed(_, "label")
    ).getOrElse(
      featuresDf
    )

    //    val data = new StringIndexer()
    //      .setInputCol(outputFieldName)
    //      .setOutputCol("label")
    //      .fit(data2).transform(data2)
    //    data.printSchema()

    //    val data1 = data.filter(data("label").===(1))
    //    val data0 = data.filter(data("label").===(0)).limit(data1.count().toInt)
    //    val merged = data0.union(data1)
    //
    //    println(data0.count())
    //    println(data1.count())
    //    println(merged.count())
  }

  private def setParam[T, M](
    paramValue: Option[T],
    setModelParam: M => (T => M))(
    model: M
  ): M =
    paramValue.map(setModelParam(model)).getOrElse(model)

  private def setSourceParam[T, S, M](
    source: S)(
    getParamValue: S => Option[T],
    setParamValue: M => (T => M))(
    target: M
  ): M =
    setParam(getParamValue(source), setParamValue)(target)

  private def chain[T](trans: (T => T)*)(init: T) =
    trans.foldLeft(init){case (a, trans) => trans(a)}

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

  private def train[M <: Model[M], Q](
    estimator: Estimator[M],
    metricWithEvaluators: Traversable[(Q, Evaluator)],
    trainData: DataFrame,
    testData: DataFrame,
    vectorizeRawPredictions: Boolean
  ): Traversable[(Q, Double, Double)] = {
    // Fit the model
    val lrModel = estimator.fit(trainData)

    val predictionVectorizer = new IndexVectorizer
    predictionVectorizer.setInputCol("prediction")
    predictionVectorizer.setOutputCol("rawPrediction")

    def getPredictions(df: DataFrame): DataFrame = {
      val predictions = lrModel.transform(df)
      val hasRawPrediction = predictions.columns.contains("rawPrediction")
      if (hasRawPrediction || !vectorizeRawPredictions)
        predictions
      else
        predictionVectorizer.transform(predictions)
    }

    val trainPredictions = getPredictions(trainData)
    val testPredictions = getPredictions(testData)

    metricWithEvaluators.map { case (metric, evaluator) =>
      try {
        val trainValue = evaluator.evaluate(trainPredictions)
        val testValue = evaluator.evaluate(testPredictions)
        Some((metric, trainValue, testValue))
      } catch {
        case e: Exception =>
          println(s"Evaluator for metric '$metric' failed.")
          None
      }
    }.flatten
  }

  private def trainWithFolds[M <: Model[M], Q](
    trainer: Estimator[M],
    metricWithEvaluators: Traversable[(Q, Evaluator)],
    folds: Option[Int],
    training: DataFrame,
    test: DataFrame,
    vectorizeRawPredictions: Boolean
  ) = {
    def trainAux[MM <: Model[MM]](estimator: Estimator[MM]) =
      train(estimator, metricWithEvaluators, training, test, vectorizeRawPredictions)

    // TODO: since we are not selecting a model cross validation here is useless
    // use cross-validation if the folds specified (without parameter optimization) and train
    folds.map { folds =>
//      val pipeline = new Pipeline().setStages(Array(trainer))
      val paramGrid = new ParamGridBuilder().build() // No parameter search

      val cv = new CrossValidator()
        .setEstimator(trainer)
        .setEstimatorParamMaps(paramGrid)
        .setEvaluator(metricWithEvaluators.head._2) // by default use the first evaluator for cross-validation
        .setNumFolds(folds)

      trainAux(cv)
    }.getOrElse(
      trainAux(trainer)
    )
  }

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

//  private def jsonsToDataFrameOld(
//    jsons: Traversable[JsObject],
//    fieldNameAndTypes: Seq[(String, FieldType[_])]
//  ): DataFrame = {
//    val data = jsons.map { json =>
//      val values = fieldNameAndTypes.map { case (fieldName, fieldType) =>
//        fieldType.jsonToDisplayString(json \ fieldName)
//      }
//      //      Row.fromSeq(values)
//      values
//    }
//
//    sparkContext.parallelize(data.toSeq).toDF(fieldNameAndTypes.map(_._1) :_*)
//  }

  private def jsonsToDataFrame(
    jsons: Traversable[JsObject],
    fieldNameAndTypes: Seq[(String, FieldType[_])],
    stringFieldsNotToIndex: Set[String] = Set()
  ): DataFrame = {
    val values = jsons.toSeq.map( json =>
      fieldNameAndTypes.map { case (fieldName, fieldType) =>
        fieldType.spec.fieldType match {
          case FieldTypeId.Enum =>
            fieldType.jsonToDisplayString(json \ fieldName)

          case FieldTypeId.Date =>
            fieldType.asValueOf[ju.Date].jsonToValue(json \ fieldName).map(date => new java.sql.Date(date.getTime)).getOrElse(null)

          case _ =>
            fieldType.jsonToValue(json \ fieldName).getOrElse(null)
        }
      }
    )

    val valueBroadVar = sparkContext.broadcast(values)
    val size = values.size

    val data: RDD[Row] = sparkContext.range(0, size).map { index =>
      Row.fromSeq(valueBroadVar.value(index.toInt))
    }

    val structTypes = fieldNameAndTypes.map { case (fieldName, fieldType) =>
      val sparkFieldType: DataType = fieldType.spec.fieldType match {
        case FieldTypeId.Integer => LongType
        case FieldTypeId.Double => DoubleType
        case FieldTypeId.Boolean => BooleanType
        case FieldTypeId.Enum => StringType
        case FieldTypeId.String => StringType
        case FieldTypeId.Date => DateType
        case FieldTypeId.Json => NullType // TODO
        case FieldTypeId.Null => NullType
      }

      // TODO: we can perhaps create our own metadata for enums without StringIndexer
//      val jsonMetadata = Json.obj("ml_attr" -> Json.obj(
//        "vals" -> JsArray(Seq(JsString("lala"), JsString("lili"))),
//        "type" -> "nominal"
//      ))
//      val metadata = Metadata.fromJson(Json.stringify(jsonMetadata))

      StructField(fieldName, sparkFieldType, true)
    }

    val stringTypes = structTypes.filter(_.dataType.equals(StringType))

//    df = session.createDataFrame(rdd_of_rows)
    val df = session.createDataFrame(data, StructType(structTypes))

    stringTypes.foldLeft(df){ case (newDf, stringType) =>
      if (!stringFieldsNotToIndex.contains(stringType.name)) {
        val randomIndex = Random.nextLong()
        val indexer = new StringIndexer().setInputCol(stringType.name).setOutputCol(stringType.name + randomIndex)
        indexer.fit(newDf).transform(newDf).drop(stringType.name).withColumnRenamed(stringType.name + randomIndex, stringType.name)
      } else {
        newDf
      }
    }
  }

  override def discretizeAsQuantiles(
    df: DataFrame,
    bucketsNum: Int,
    columnName: String
  ): DataFrame = {
    val outputColumnName = columnName + "_" + Random.nextLong()
    val discretizer = new QuantileDiscretizer()
      .setInputCol(columnName)
      .setOutputCol(outputColumnName)
      .setNumBuckets(bucketsNum)

    val result = discretizer.fit(df).transform(df)

    result.drop(columnName).withColumnRenamed(outputColumnName, columnName)
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
    val df = jsonsToLearningDataFrame(data, fields, Some(outputFieldName), Some(discretizerBucketsNum))
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

  override def independenceTest(
    df: DataFrame
  ): Seq[ChiSquareResult] = {
    val resultDf = ChiSquareTest.test(df, "features", "label")

    val chi = resultDf.head

    val pValues = chi.getAs[Vector](0).toArray.toSeq
    val degreesOfFreedom = chi.getSeq[Int](1)
    val statistics = chi.getAs[Vector](2).toArray.toSeq

    (pValues, degreesOfFreedom, statistics).zipped.map(
      ChiSquareResult(_, _, _)
    )
  }

  override def independenceTest(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    targetFieldName: String
  ): Seq[(String, ChiSquareResult)] = {
    // prepare the features->label data frame
    val df = jsonsToLearningDataFrame(data, fields, Some(targetFieldName), Some(10))
    val inputDf = BooleanLabelIndexer.transform(df)

    // run the chi-square independence test
    val results = independenceTest(inputDf)

    val featureNames = inputDf.columns.filterNot(columnName => columnName.equals("features") || columnName.equals("label"))
    featureNames.zip(results)
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
  val trainingTestResults: Traversable[(Double, Double)]
) extends Performance {
  override type T = ClassificationEvalMetric.Value
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

      result.trainingTestResults.foreach { case (trainValue, testValue) =>
        trainingStats.addValue(trainValue)
        testStats.addValue(testValue)
      }

      (result.evalMetric, (toStats(trainingStats), toStats(testStats)))
    }.toMap
  }

  def createClassificationResult(
    results: Traversable[ClassificationPerformance],
    setting: ClassificationSetting
  ): ClassificationResult =
    createClassificationResult(
      calcClassificationMetricStats(results),
      setting
    )

  def createClassificationResult(
    evalMetricStatsMap: Map[ClassificationEvalMetric.Value, (MetricStatsValues, MetricStatsValues)],
    setting: ClassificationSetting
  ): ClassificationResult = {
    // helper functions
    def trainingStatsOptional(metric: ClassificationEvalMetric.Value) =
      evalMetricStatsMap.get(metric).map(_._1)

    def testStatsOptional(metric: ClassificationEvalMetric.Value) =
      evalMetricStatsMap.get(metric).map(_._2)

    def trainingStats(metric: ClassificationEvalMetric.Value) =
      trainingStatsOptional(metric).getOrElse(
        throw new AdaException(s"Classification stats for metics '${metric.toString}' not found.")
      )

    def testStats(metric: ClassificationEvalMetric.Value) =
      testStatsOptional(metric).getOrElse(
        throw new AdaException(s"Classification stats for metics '${metric.toString}' not found.")
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

    ClassificationResult(
      None,
      setting.copy(inputFieldNames = setting.inputFieldNames.sorted),
      trainingMetricStats,
      testMetricStats
    )
  }
}

case class ChiSquareResult(
  pValue: Double, degreeOfFreedom: Int, statistics: Double
)

object ChiSquareResult {
  implicit val chiSquareResultFormat = Json.format[ChiSquareResult]
}