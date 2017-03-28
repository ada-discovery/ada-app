package services.ml

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import dataaccess.{FieldType, FieldTypeHelper}
import models.FieldTypeId
import models.ml.classification.Classification
import models.ml.regression.Regression
import org.apache.spark.ml.evaluation.{Evaluator, MulticlassClassificationEvaluator, RegressionEvaluator}
import org.apache.spark.ml.feature.{StringIndexer, VectorAssembler}
import org.apache.spark.ml.{Estimator, Model}
import org.apache.spark.sql.types.{Metadata, MetadataBuilder, _}
import org.apache.spark.sql.{DataFrame, Row}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import services.SparkApp

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

@ImplementedBy(classOf[MachineLearningServiceImpl])
trait MachineLearningService {

  def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldNames: String,
    mlModel: Classification
  ): Double

  def regress(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldNames: String,
    mlModel: Regression
  ): Double

  // AFTSurvivalRegression
  // IsotonicRegression
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

  override def classify(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldName: String,
    mlModel: Classification
  ): Double = {
    val trainer = SparkMLEstimatorFactory(mlModel)

    val evaluator = new MulticlassClassificationEvaluator()
      .setLabelCol("label")
      .setPredictionCol("prediction")
      .setMetricName("accuracy")

    val dataFrame = loadDataFrame(dataSetId, featureFieldNames, outputFieldName)
    val Array(training, test) = dataFrame.randomSplit(Array(0.7, 0.3), seed = 11L)

    train(trainer, evaluator, training, test)
  }

  override def regress(
    dataSetId: String,
    featureFieldNames: Seq[String],
    outputFieldName: String,
    mlModel: Regression
  ): Double = {
    val trainer = SparkMLEstimatorFactory(mlModel)
    val evaluator = new RegressionEvaluator()
      .setLabelCol("label")
      .setPredictionCol("prediction")
      .setMetricName("rmse")

    val dataFrame = loadDataFrame(dataSetId, featureFieldNames, outputFieldName)
    val Array(training, test) = dataFrame.randomSplit(Array(0.7, 0.3), seed = 11L)

    train(trainer, evaluator, training, test)
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
    df.schema.fields.foreach(field =>
      println(s"${field.name}: ${field.dataType.typeName} (nullable = ${field.nullable}), metadata = ${field.metadata.toString()}")
    )

    val assembler = new VectorAssembler()
      .setInputCols(df.columns.filter(featureFieldNames.contains))
      .setOutputCol("features")

    val data = assembler.transform(df).withColumnRenamed(outputFieldName, "label")

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
    evaluator: Evaluator,
    trainData: DataFrame,
    testData: DataFrame
  ): Double = {
    // Fit the model
    val lrModel = reg.fit(trainData)

    // Make predictions.
    val predictions = lrModel.transform(testData)

    evaluator.evaluate(predictions)
  }

  private def dataSetToDataFrame(dsa: DataSetAccessor) =
    for {
      fields <- dsa.fieldRepo.find()
      jsons <- dsa.dataSetRepo.find()
    } yield {
      val fieldNameAndTypes = fields.map(field => (field.name, ftf(field.fieldTypeSpec)))
      jsonsToDataFrame(jsons, fieldNameAndTypes.toSeq)
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

      // TODO: we can pehaps create our own metadata for enums without StringIndexer
//      val jsonMetadata = Json.obj("ml_attr" -> Json.obj(
//        "vals" -> JsArray(Seq(JsString("lala"), JsString("lili"))),
//        "type" -> "nominal"
//      ))
//      val metadata = Metadata.fromJson(Json.stringify(jsonMetadata))

      StructField(fieldName, sparkFieldType, true)
    }

    val stringTypes = structTypes.filter(_.dataType.equals(StringType))

    val df = session.createDataFrame(rdds, StructType(structTypes))

    stringTypes.foldLeft(df){ case (newDf, stringType) =>
      val randomIndex = Random.nextLong()
      val indexer = new StringIndexer().setInputCol(stringType.name).setOutputCol(stringType.name + randomIndex)
      indexer.fit(newDf).transform(newDf).drop(stringType.name).withColumnRenamed(stringType.name + randomIndex, stringType.name)
    }
  }
}