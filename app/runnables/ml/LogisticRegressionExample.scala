package runnables.ml

import javax.inject.Inject

import field.{FieldType, FieldTypeHelper}
import models.FieldTypeId
import org.apache.spark.ml._
import org.apache.spark.ml.classification._
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, MulticlassClassificationEvaluator}
import org.apache.spark.ml.feature.{StringIndexer, VectorAssembler}
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json.JsObject
import org.incal.play.GuiceRunnableApp
import services.SparkApp

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
// import org.apache.ignite.spark.IgniteContext

class LogisticRegressionExample @Inject() (
    sparkApp: SparkApp,
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  private val dsa = dsaf("ppmi.ppmi_si").get
  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val rootFolder = "/home/peter/Downloads/spark-master/"

  val session = sparkApp.session
  val sparkContext = sparkApp.sc
  implicit val sqlContext = sparkApp.sqlContext
  import sqlContext.implicits._


  //  val igniteContext = new IgniteContext(sparkContext,
//    () => new IgniteConfiguration())

  private val labelColumnName = "GROUP"
  private val nonFeatureColumns = Set("GROUP", "ID")

  override def run() {
    // Load training data
    val df = Await.result(dataSetToDataFrame(dsa), 2 minutes)
    df.printSchema()

    val assembler = new VectorAssembler()
      .setInputCols(df.columns.filterNot(nonFeatureColumns.contains))
      .setOutputCol("features")

    val data2 = assembler.transform(df) // .withColumnRenamed("GROUP", "label")

    val data = new StringIndexer()
      .setInputCol("GROUP")
      .setOutputCol("label")
      .fit(data2).transform(data2)

//    val data = assembler.transform(df).withColumnRenamed("GROUP", "label")

//    val data = session.read.format("libsvm").load(rootFolder + "data/mllib/sample_libsvm_data.txt")
    data.printSchema()

    val data1 = data.filter(data("label").===(1))
    val data0 = data.filter(data("label").===(0)).limit(data1.count().toInt)
    val merged = data0.union(data1)

    println(data0.count())
    println(data1.count())
    println(merged.count())
    val Array(training, test) = merged.randomSplit(Array(0.7, 0.3), seed = 11L)

    println("Regression")

//    val cv = new CrossValidator()
//      .setEstimator(pipeline)
//      .setEvaluator(evaluator)
//      .setEstimatorParamMaps(paramGrid)
//      .setNumFolds(nFolds)
//
//    val model = cv.fit(trainingData) // tr

    val lr = new LogisticRegression()
      .setMaxIter(10000)
      .setRegParam(0.3)
      .setElasticNetParam(0.8)
//      .setThreshold(0.5)

    train[LogisticRegressionModel](lr, training, test)

    println("Multinomial Regression")

    // We can also use the multinomial family for binary classification
    val mlr = new LogisticRegression()
      .setMaxIter(10000)
      .setRegParam(0.3)
      .setElasticNetParam(0.8)
      .setFamily("multinomial")
//      .setThreshold(0.5)

    train(mlr, training, test)

    println("Random Forest Classifier")

    val rf = new RandomForestClassifier()
//      .setLabelCol("indexedLabel")
//      .setFeaturesCol("indexedFeatures")
      .setNumTrees(20)

    train(rf, training, test)

    println("GBT Classifier")

    val gbt = new GBTClassifier()
      //      .setLabelCol("indexedLabel")
      //      .setFeaturesCol("indexedFeatures")
      .setMaxIter(200)

    train(gbt, training, test)

    println("Multilayer Perceptron Classifier")

    for (i <- 10 to 100) {
      println("Hidden layers neurons " + i)
      val layers = Array[Int](11, i, i, 2)

      val trainer = new MultilayerPerceptronClassifier()
        .setLayers(layers)
        .setBlockSize(100)
        .setSeed(1234L)
        .setMaxIter(5000)

      train(trainer, training, test)
    }
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

  private def train[M <: Model[M]](
    reg: Estimator[M],
    trainData: DataFrame,
    testData: DataFrame
  ) = {
    // Fit the model
    val lrModel = reg.fit(trainData)

    // Make predictions.
    val predictions = lrModel.transform(testData)
    predictions.printSchema()

    // Select example rows to display.
    predictions.select("prediction", "label", "features").show(20)

    // Select (prediction, true label) and compute test error
    val evaluator = new BinaryClassificationEvaluator()

    val evaluator2 = new MulticlassClassificationEvaluator()
      .setLabelCol("label")
      .setPredictionCol("prediction")
      .setMetricName("accuracy")

    val accuracy = evaluator2.evaluate(predictions)
    println("Test Error = " + (1.0 - accuracy))
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

//  def ofRows(sparkSession: SparkSession, logicalPlan: LogicalPlan): DataFrame = {
//    val qe = sparkSession.sessionState.executePlan(logicalPlan)
//    qe.assertAnalyzed()
//    new Dataset[Row](sparkSession, qe, RowEncoder(qe.analyzed.schema))
//  }
}

object LogisticRegressionExample extends GuiceRunnableApp[LogisticRegressionExample]