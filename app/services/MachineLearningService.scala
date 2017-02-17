package services

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import dataaccess.{FieldType, FieldTypeHelper}
import models.FieldTypeId
import models.ml._
import org.apache.spark.ml.{Estimator, Model}
import org.apache.spark.ml.classification
import classification._
import classification.{NaiveBayes => NaiveBayesClassifier}
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, MulticlassClassificationEvaluator}
import org.apache.spark.ml.feature.{StringIndexer, VectorAssembler}
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Row}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json.JsObject

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[MachineLearningServiceImpl])
trait MachineLearningService {

  def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldNames: String,
    mlModel: MultiLayerPerceptron
  ): Double

  def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldNames: String,
    mlModel: Regression
  ): Double

  def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldNames: String,
    mlModel: DecisionTree
  ): Double

  def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldNames: String,
    mlModel: RandomForest
  ): Double

  def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldNames: String,
    mlModel: GradientBoostTree
  ): Double

  def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldName: String,
    mlModel: models.ml.NaiveBayes
  ): Double
}

@Singleton
private class MachineLearningServiceImpl @Inject() (
    sparkApp: SparkApp,
    dsaf: DataSetAccessorFactory
  ) extends MachineLearningService {

  private val ftf = FieldTypeHelper.fieldTypeFactory

  private val session = sparkApp.session
  private val sparkContext = sparkApp.sc
  private implicit val sqlContext = sparkApp.sqlContext
  import sqlContext.implicits._

  override def classify(
    dataSetId: String,
    featureFields: Seq[String],
    outputField: String,
    mlModel: MultiLayerPerceptron
  ): Double = {
    def set[T] = setSourceParam[T, MultiLayerPerceptron, MultilayerPerceptronClassifier](mlModel)_

    val trainer = chain(
      set(_.blockSize, _.setBlockSize),
      set(_.seed, _.setSeed),
      set(_.maxIteration, _.setMaxIter),
      set(_.solver.map(_.toString), _.setSolver),
      set(_.stepSize, _.setStepSize),
      set(_.tolerance, _.setTol),
      set(o => Some(o.layers.toArray), _.setLayers)
    )(new MultilayerPerceptronClassifier())

    val df = loadDataFrame(dataSetId, featureFields, outputField)

    val Array(training, test) = df.randomSplit(Array(0.7, 0.3), seed = 11L)
    train(trainer, training, test)
  }

  override def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldName: String,
    mlModel: Regression
  ): Double = {
    def set[T] = setSourceParam[T, Regression, LogisticRegression](mlModel)_

    val trainer = chain(
      set(_.aggregationDepth, _.setAggregationDepth),
      set(_.elasticMixingRatio, _.setElasticNetParam),
      set(_.family.map(_.toString), _.setFamily),
      set(_.fitIntercept, _.setFitIntercept),
      set(_.maxIteration, _.setMaxIter),
      set(_.regularization, _.setRegParam),
      set(_.threshold, _.setThreshold),
      set(_.standardization, _.setStandardization),
      set(_.tolerance, _.setTol)
    )(new LogisticRegression())

    val df = loadDataFrame(dataSetId, featureFieldNames, outputFieldName)

    val Array(training, test) = df.randomSplit(Array(0.7, 0.3), seed = 11L)
    train(trainer, training, test)
  }

  override def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldName: String,
    mlModel: DecisionTree
  ): Double = {
    def set[T] = setSourceParam[T, DecisionTree, DecisionTreeClassifier](mlModel)_

    val trainer = chain(
      set(_.maxDepth, _.setMaxDepth),
      set(_.maxBins, _.setMaxBins),
      set(_.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.minInfoGain, _.setMinInfoGain),
      set(_.seed, _.setSeed),
      set(_.impurity.map(_.toString), _.setImpurity)
    )(new DecisionTreeClassifier())

    val df = loadDataFrame(dataSetId, featureFieldNames, outputFieldName)

    val Array(training, test) = df.randomSplit(Array(0.7, 0.3), seed = 11L)
    train(trainer, training, test)
  }

  override def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldName: String,
    mlModel: RandomForest
  ): Double = {
    def set[T] = setSourceParam[T, RandomForest, RandomForestClassifier](mlModel)_

    val trainer = chain(
      set(_.numTrees, _.setNumTrees),
      set(_.maxDepth, _.setMaxDepth),
      set(_.maxBins, _.setMaxBins),
      set(_.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.minInfoGain, _.setMinInfoGain),
      set(_.seed, _.setSeed),
      set(_.subsamplingRate, _.setSubsamplingRate),
      set(_.impurity.map(_.toString), _.setImpurity),
      set(_.featureSubsetStrategy.map(_.toString), _.setFeatureSubsetStrategy)
    )(new RandomForestClassifier())

    val df = loadDataFrame(dataSetId, featureFieldNames, outputFieldName)

    val Array(training, test) = df.randomSplit(Array(0.7, 0.3), seed = 11L)
    train(trainer, training, test)
  }

  override def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldName: String,
    mlModel: GradientBoostTree
  ): Double = {
    def set[T] = setSourceParam[T, GradientBoostTree, GBTClassifier](mlModel)_

    val trainer = chain(
      set(_.maxIteration, _.setMaxIter),
      set(_.stepSize, _.setStepSize),
      set(_.maxDepth, _.setMaxDepth),
      set(_.maxBins, _.setMaxBins),
      set(_.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.minInfoGain, _.setMinInfoGain),
      set(_.seed, _.setSeed),
      set(_.subsamplingRate, _.setSubsamplingRate),
      set(_.impurity.map(_.toString), _.setImpurity)
    )(new GBTClassifier())

    val df = loadDataFrame(dataSetId, featureFieldNames, outputFieldName)

    val Array(training, test) = df.randomSplit(Array(0.7, 0.3), seed = 11L)
    train(trainer, training, test)
  }

  override def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldName: String,
    mlModel: models.ml.NaiveBayes
  ): Double = {
    def set[T] = setSourceParam[T, models.ml.NaiveBayes, NaiveBayesClassifier](mlModel)_

    val trainer = chain(
      set(_.smoothing, _.setSmoothing),
      set(_.modelType.map(_.toString), _.setModelType)
    )(new NaiveBayesClassifier())

    val df = loadDataFrame(dataSetId, featureFieldNames, outputFieldName)

    val Array(training, test) = df.randomSplit(Array(0.7, 0.3), seed = 11L)
    train(trainer, training, test)
  }

  private def loadDataFrame(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldName: String
  ): DataFrame = {
    val dsa = dsaf(dataSetId).get
    // Load training data
    val df = Await.result(dataSetToDataFrame(dsa), 2 minutes)
//    df.printSchema()

    val assembler = new VectorAssembler()
      .setInputCols(df.columns.filter(featureFieldNames.contains))
      .setOutputCol("features")

    val data2 = assembler.transform(df) // .withColumnRenamed(outputField, "label")

    val data = new StringIndexer()
      .setInputCol(outputFieldName)
      .setOutputCol("label")
      .fit(data2).transform(data2)
//    data.printSchema()

    //    val data1 = data.filter(data("label").===(1))
    //    val data0 = data.filter(data("label").===(0)).limit(data1.count().toInt)
    //    val merged = data0.union(data1)
    //
    //    println(data0.count())
    //    println(data1.count())
    //    println(merged.count())
    data
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

  private def train[M <: Model[M]](
    reg: Estimator[M],
    trainData: DataFrame,
    testData: DataFrame
  ): Double = {
    // Fit the model
    val lrModel = reg.fit(trainData)

    // Make predictions.
    val predictions = lrModel.transform(testData)
//    predictions.printSchema()

    // Select example rows to display.
//    predictions.select("prediction", "label", "features").show(20)

    // Select (prediction, true label) and compute test error
//    val evaluator = new BinaryClassificationEvaluator()

    val evaluator = new MulticlassClassificationEvaluator()
      .setLabelCol("label")
      .setPredictionCol("prediction")
      .setMetricName("accuracy")

    val accuracy = evaluator.evaluate(predictions)
    println("Test Error = " + (1.0 - accuracy))
    accuracy
  }

  private def dataSetToDataFrame(dsa: DataSetAccessor) =
    for {
      fields <- dsa.fieldRepo.find()
      jsons <- dsa.dataSetRepo.find()
    } yield {
      val fieldNameAndTypes = fields.map(field => (field.name, ftf(field.fieldTypeSpec)))
      jsonsToDataFrame(jsons, fieldNameAndTypes.toSeq)
    }

  private def jsonsToDataFrameOld(
    jsons: Traversable[JsObject],
    fieldNameAndTypes: Seq[(String, FieldType[_])]
  ): DataFrame = {
    val data = jsons.map { json =>
      val values = fieldNameAndTypes.map { case (fieldName, fieldType) =>
        fieldType.jsonToDisplayString(json \ fieldName)
      }
      //      Row.fromSeq(values)
      values
    }

    sparkContext.parallelize(data.toSeq).toDF(fieldNameAndTypes.map(_._1) :_*)
  }

  private def jsonsToDataFrame(
    jsons: Traversable[JsObject],
    fieldNameAndTypes: Seq[(String, FieldType[_])]
  ): DataFrame = {
    val data = jsons.map { json =>
      val values = fieldNameAndTypes.map { case (fieldName, fieldType) =>
        if (fieldType.spec.fieldType == FieldTypeId.Enum) {
          fieldType.jsonToDisplayString(json \ fieldName)
        } else {
          fieldType.jsonToValue(json \ fieldName).getOrElse(null)
        }
      }
      Row.fromSeq(values)
    }

    val rdds = sparkContext.parallelize(data.toSeq)

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
      StructField(fieldName, sparkFieldType, true)
    }

    session.createDataFrame(rdds, StructType(structTypes))
  }
}