package services.ml

import javax.inject.{Inject, Singleton}
import java.{util => ju}

import com.google.inject.ImplementedBy
import dataaccess.{FieldType, FieldTypeHelper}
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{FieldTypeId, FieldTypeSpec}
import models.ml.classification.Classification
import models.ml.regression.Regression
import models.ml.unsupervised.UnsupervisedLearning
import org.apache.spark.ml.evaluation.{Evaluator, MulticlassClassificationEvaluator, RegressionEvaluator}
import org.apache.spark.ml.feature.{StringIndexer, VectorAssembler}
import org.apache.spark.ml.{Estimator, Model}
import org.apache.spark.sql.types.{Metadata, MetadataBuilder, _}
import org.apache.spark.sql.{DataFrame, Row}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.apache.spark.ml.clustering._
import org.apache.spark.ml.linalg.DenseVector
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import reactivemongo.bson.BSONObjectID
import services.SparkApp

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

@ImplementedBy(classOf[MachineLearningServiceImpl])
trait MachineLearningService {

  def classify(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Classification
  ): Traversable[(ClassificationEvalMetric.Value, Double)]

  def regress(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Regression
  ): Traversable[(RegressionEvalMetric.Value, Double)]

  def learnUnsupervised[M <: Model[M]](
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning
  ): Traversable[(String, Int)]

    // AFTSurvivalRegression
  // IsotonicRegression
}

@Singleton
private class MachineLearningServiceImpl @Inject() (sparkApp: SparkApp) extends MachineLearningService {

  private val ftf = FieldTypeHelper.fieldTypeFactory

  private val session = sparkApp.session
  private val sparkContext = sparkApp.sc
  private implicit val sqlContext = sparkApp.sqlContext

  override def classify(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Classification
  ): Traversable[(ClassificationEvalMetric.Value, Double)] = {
    val trainer = SparkMLEstimatorFactory(mlModel)

    val evaluators = ClassificationEvalMetric.values.toSeq.map { metric =>
      val evaluator = new MulticlassClassificationEvaluator()
        .setLabelCol("label")
        .setPredictionCol("prediction")
        .setMetricName(metric.toString)

      (metric, evaluator)
    }

    val dataFrame = jsonsToLearningDataFrame(data, fields, Some(outputFieldName))

    // make sure the output is string

    val finalDf = dataFrame.schema("label").dataType match {
      case BooleanType =>
        val newDf = dataFrame.withColumn("label", dataFrame("label").cast(StringType))
        val indexer = new StringIndexer().setInputCol("label").setOutputCol("label_new_temp")
        indexer.fit(newDf).transform(newDf).drop("label").withColumnRenamed("label_new_temp", "label")

      case _ => dataFrame
    }

    val Array(training, test) = finalDf.randomSplit(Array(0.7, 0.3), seed = 11L)

    train(trainer, evaluators, training, test)
  }

  override def regress(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Regression
  ): Traversable[(RegressionEvalMetric.Value, Double)] = {
    val trainer = SparkMLEstimatorFactory(mlModel)

    val dataFrame = jsonsToLearningDataFrame(data, fields, Some(outputFieldName))
    val Array(training, test) = dataFrame.randomSplit(Array(0.7, 0.3), seed = 11L)

    val evaluators = RegressionEvalMetric.values.toSeq.map { metric =>

      val evaluator = new RegressionEvaluator()
        .setLabelCol("label")
        .setPredictionCol("prediction")
        .setMetricName(metric.toString)

      (metric, evaluator)
    }

    train(trainer, evaluators, training, test)
  }

  override def learnUnsupervised[M <: Model[M]](
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning
  ): Traversable[(String, Int)] = {
    val trainer = SparkMLEstimatorFactory[M](mlModel)

    // prepare a data frame for learning
    val featureFieldNames = fields.map(_._1)
    val fieldsWithId = fields ++ Seq((JsObjectIdentity.name, FieldTypeSpec(FieldTypeId.String)))
    val dataFrame = jsonsToLearningDataFrame(data, fieldsWithId, featureFieldNames)

    val (model, predictions) = fit(trainer, dataFrame)

    def extractClusterClasses(columnName: String): Traversable[(String, Int)] =
      predictions.select(JsObjectIdentity.name, columnName).rdd.map { r =>
        val id = r(0).asInstanceOf[String]
        val clazz = r(1).asInstanceOf[Int]
        (id, clazz)
      }.collect

    def extractClusterClasssedFromProbabilities(columnName: String): Traversable[(String, Int)] =
      predictions.select(JsObjectIdentity.name, columnName).rdd.map { r =>
        val id = r(0).asInstanceOf[String]
        val clazz = r(1).asInstanceOf[DenseVector].values.zipWithIndex.maxBy(_._1)._2
        (id, clazz)
      }.collect

    model match {
      case m: KMeansModel =>
        // Evaluate clustering by computing Within Set Sum of Squared Errors.
//        val WSSSE = m.computeCost(dataFrame)
//        println(s"Within Set Sum of Squared Errors = $WSSSE")

//        // Shows the result.
//        println("Cluster Centers:")
//        m.clusterCenters.foreach(println)

        // extract cluster classes
        extractClusterClasses("prediction")

      case m: LDAModel =>
//        val ll = m.logLikelihood(dataFrame)
//        val lp = m.logPerplexity(dataFrame)
//        println(s"The lower bound on the log likelihood of the entire corpus: $ll")
//        println(s"The upper bound bound on perplexity: $lp")
//
//        // Describe topics.
//        val topics = m.describeTopics(3)
//        println("The topics described by their top-weighted terms:")
//        topics.show(false)

//        // Shows the result.
//        val transformed = model.transform(dataFrame)
//        transformed.show(false)

        // extract cluster classes
        extractClusterClasssedFromProbabilities("topicDistribution")

      case m: BisectingKMeansModel =>
        // Evaluate clustering.
//        val cost = m.computeCost(dataFrame)
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
  }

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

    prepLearningDataFrame(df, featureFieldNames, None)
  }

  private def jsonsToLearningDataFrame(
    jsons: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: Option[String]
  ): DataFrame = {
    // convert jsons to a data frame
    val fieldNameAndTypes = fields.map { case (name, fieldTypeSpec) => (name, ftf(fieldTypeSpec))}
    val df = jsonsToDataFrame(jsons, fieldNameAndTypes)

    // prep the data frame for learning
    val featureFieldNames = outputFieldName.map( outputName =>
      fields.map(_._1).filterNot(_ == outputName)
    ).getOrElse(
      fields.map(_._1)
    )

    prepLearningDataFrame(df, featureFieldNames, outputFieldName)
  }

  private def prepLearningDataFrame(
    df: DataFrame,
    featureFieldNames: Seq[String],
    outputFieldName: Option[String]
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
    testData: DataFrame
  ): Traversable[(Q, Double)] = {
    // Fit the model
    val lrModel = estimator.fit(trainData)

    // Make predictions.
    val predictions = lrModel.transform(testData)

    metricWithEvaluators.map{ case (metric, evaluator) => (metric, evaluator.evaluate(predictions))}
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
    val data = jsons.map { json =>
      val values = fieldNameAndTypes.map { case (fieldName, fieldType) =>
        fieldType.spec.fieldType match {
          case FieldTypeId.Enum =>
            fieldType.jsonToDisplayString(json \ fieldName)

          case FieldTypeId.Date =>
            fieldType.asValueOf[ju.Date].jsonToValue(json \ fieldName).map( date => new java.sql.Date(date.getTime)).getOrElse(null)

          case _ =>
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

      // TODO: we can perhaps create our own metadata for enums without StringIndexer
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
      if (!stringFieldsNotToIndex.contains(stringType.name)) {
        val randomIndex = Random.nextLong()
        val indexer = new StringIndexer().setInputCol(stringType.name).setOutputCol(stringType.name + randomIndex)
        indexer.fit(newDf).transform(newDf).drop(stringType.name).withColumnRenamed(stringType.name + randomIndex, stringType.name)
      } else {
        newDf
      }
    }
  }
}

object ClassificationEvalMetric extends Enumeration {
  val f1, weightedPrecision, weightedRecall, accuracy = Value
}

object RegressionEvalMetric extends Enumeration {
  val mse, rmse, r2, mae = Value
}

