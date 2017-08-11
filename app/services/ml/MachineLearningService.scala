package services.ml

import java.util.Collections
import javax.inject.{Inject, Singleton}
import java.{lang => jl, util => ju}

import com.banda.core.util.ObjectUtil
import com.banda.incal.domain.ReservoirLearningSetting
import com.banda.incal.prediction.{ErrorMeasures, ReservoirTrainerFactory}
import com.banda.math.business.learning.{IOStream, IOStreamFactory}
import com.banda.network.domain.{ActivationFunctionType, TopologicalNode, Topology}
import com.google.inject.ImplementedBy
import dataaccess.{AscSort, FieldType, FieldTypeHelper}
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{FieldTypeId, FieldTypeSpec}
import models.ml.classification.Classification
import models.ml.regression.Regression
import models.ml.unsupervised.UnsupervisedLearning
import com.banda.incal.prediction.ErrorMeasures
import com.banda.math.business.MathUtil
import com.banda.network.business.TopologyFactory
import models.ml.{LearningSetting, VectorTransformType}
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, Evaluator, MulticlassClassificationEvaluator, RegressionEvaluator}
import org.apache.spark.ml.feature._
import org.apache.spark.ml.{Estimator, Model, Pipeline}
import org.apache.spark.sql.types.{Metadata, MetadataBuilder, _}
import org.apache.spark.sql.{DataFrame, Row}
import org.apache.spark.ml.clustering._
import org.apache.spark.ml.linalg.DenseVector
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import play.api.libs.json.JsObject
import services.{RCPredictionResults, SparkApp, SparkUtil}
import org.apache.spark.sql.functions.monotonically_increasing_id

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.collection.JavaConversions._

@ImplementedBy(classOf[MachineLearningServiceImpl])
trait MachineLearningService {

  def classify(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Classification,
    setting: LearningSetting = LearningSetting()
  ): Traversable[ClassificationPerformance]

  def regress(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Regression,
    setting: LearningSetting = LearningSetting()
  ): Traversable[RegressionPerformance]

  def learnUnsupervised[M <: Model[M]](
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    pcaDim: Option[Int] = None
  ): Traversable[(String, Int)]

  def predictRCTimeSeries(
    reservoirSetting: ReservoirLearningSetting,
    topology: Topology,
    predictAhead: Int,
    inputSeries: Seq[Seq[jl.Double]],
    targetSeries: Seq[jl.Double]
  ): Future[RCPredictionResults]

  def transformVectors(
    data: DataFrame,
    transformType: VectorTransformType.Value,
    inRow: Boolean = false
  ): DataFrame

  // AFTSurvivalRegression
  // IsotonicRegression
}

@Singleton
private class MachineLearningServiceImpl @Inject() (
    sparkApp: SparkApp,
    ioStreamFactory: IOStreamFactory,
    reservoirTrainerFactory: ReservoirTrainerFactory,
    topologyFactory: TopologyFactory
  ) extends MachineLearningService {

  private val ftf = FieldTypeHelper.fieldTypeFactory

  private val session = sparkApp.session
  private val sparkContext = sparkApp.sc
  private implicit val sqlContext = sparkApp.sqlContext

  private val defaultTrainingTestingSplit = 0.8

  override def classify(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Classification,
    setting: LearningSetting
  ): Traversable[ClassificationPerformance] = {
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

    // reduce the dimensionality if needed
    val dataFrame = setting.pcaDims.map( pcaDims =>
      principalFeatureComponents(df, pcaDims)
    ).getOrElse(df)

    // make sure the output is string

    val finalDf = dataFrame.schema("label").dataType match {
      case BooleanType =>
        val newDf = dataFrame.withColumn("label", dataFrame("label").cast(StringType))
        val indexer = new StringIndexer().setInputCol("label").setOutputCol("label_new_temp")
        indexer.fit(newDf).transform(newDf).drop("label").withColumnRenamed("label_new_temp", "label")

      case _ => dataFrame
    }

    val cachedDf = finalDf.cache()

    // split the data into training and test parts
    val split = setting.trainingTestingSplit.getOrElse(defaultTrainingTestingSplit)

    val results = (0  until setting.repetitions.getOrElse(1)).map { _ =>
      val Array(training, test) = cachedDf.randomSplit(Array(split, 1 - split))

      // run the trainer (with folds) on the given training and test data sets
      trainWithFolds(trainer, evaluators ++ binEvaluators, setting.crossValidationFolds, training, test)
    }

    // uncache
    cachedDf.unpersist()

    // create performance results
    results.flatten.groupBy(_._1).map { case (evalMetric, results) =>
      ClassificationPerformance(evalMetric, results.map( x => (x._2, x._3)))
    }
  }

  override def regress(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Regression,
    setting: LearningSetting
  ): Traversable[RegressionPerformance] = {
    val trainer = SparkMLEstimatorFactory(mlModel)

    val df = jsonsToLearningDataFrame(data, fields, Some(outputFieldName))

    // reduce the dimensionality if needed
    val dataFrame = setting.pcaDims.map(pcaDims =>
      principalFeatureComponents(df, pcaDims)
    ).getOrElse(df)

    val evaluators = RegressionEvalMetric.values.toSeq.map { metric =>

      val evaluator = new RegressionEvaluator()
        .setLabelCol("label")
        .setPredictionCol("prediction")
        .setMetricName(metric.toString)

      (metric, evaluator)
    }

    val cachedDf = dataFrame.cache()

    // split the data into training and test parts
    val split = setting.trainingTestingSplit.getOrElse(defaultTrainingTestingSplit)

    val results = (0  until setting.repetitions.getOrElse(1)).map { _ =>
      val Array(training, test) = cachedDf.randomSplit(Array(split, 1 - split))

      // run the trainer (with folds) on the given training and test data sets
      trainWithFolds(trainer, evaluators, setting.crossValidationFolds, training, test)
    }

    // uncache
    cachedDf.unpersist()

    // create performance results
    results.flatten.groupBy(_._1).map { case (evalMetric, results) =>
      RegressionPerformance(evalMetric, results.map( x => (x._2, x._3)))
    }
  }

  override def learnUnsupervised[M <: Model[M]](
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    pcaDim: Option[Int]
  ): Traversable[(String, Int)] = {
    val trainer = SparkMLEstimatorFactory[M](mlModel)

    // prepare a data frame for learning
    val featureFieldNames = fields.map(_._1)
    val fieldsWithId = fields ++ Seq((JsObjectIdentity.name, FieldTypeSpec(FieldTypeId.String)))
    val df = jsonsToLearningDataFrame(data, fieldsWithId, featureFieldNames)

    // reduce the dimensionality if needed
    val dataFrame = if (pcaDim.isDefined)
      principalFeatureComponents(df, pcaDim.get)
    else
      df

    val cachedDf = dataFrame.cache()

    val (model, predictions) = fit(trainer, cachedDf)

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
  }

  private def principalFeatureComponents(
    df: DataFrame,
    k: Int
  ) = {
    val pca = new PCA()
      .setInputCol("features")
      .setOutputCol("pcaFeatures")
      .setK(k)
      .fit(df)

    // replace in-place
    pca.transform(df).drop("features").withColumnRenamed("pcaFeatures", "features")
  }

  override def transformVectors(
    data: DataFrame,
    transformType: VectorTransformType.Value,
    inRow: Boolean = false
  ) = {
    // aux function to transpose features
    def transpose(columnNames: Traversable[String], df: DataFrame) =
      SparkUtil.transposeVectors(session, columnNames, df)

    // for normalizers we have to switch rows wth columns
    val isNormalizer = transformType == VectorTransformType.L1Normalizer || transformType == VectorTransformType.L2Normalizer

    // check if transpose is needed
    val transposeNeeded = (isNormalizer && !inRow) || (!isNormalizer && inRow)

    // create input df (apply transpose optionally)
    val inputDf = if (transposeNeeded) transpose(Seq("features"), data) else data

    val transformer = transformType match {
      case VectorTransformType.L1Normalizer =>
        new Normalizer()
          .setInputCol("features")
          .setOutputCol("scaledFeatures")
          .setP(1.0)

      case VectorTransformType.L2Normalizer =>
        new Normalizer()
          .setInputCol("features")
          .setOutputCol("scaledFeatures")
          .setP(2.0)

      case VectorTransformType.StandardScaler =>
        new StandardScaler()
          .setInputCol("features")
          .setOutputCol("scaledFeatures")
          .setWithStd(true)
          .setWithMean(true).fit(inputDf)

      case VectorTransformType.MinMaxPlusMinusOneScaler =>
        new MinMaxScaler()
          .setInputCol("features")
          .setOutputCol("scaledFeatures")
          .setMin(-1)
          .setMax(1).fit(inputDf)

      case VectorTransformType.MinMaxZeroOneScaler =>
        new MinMaxScaler()
          .setInputCol("features")
          .setOutputCol("scaledFeatures")
          .setMin(0)
          .setMax(1).fit(inputDf)

      case VectorTransformType.MaxAbsScaler =>
        new MaxAbsScaler()
          .setInputCol("features")
          .setOutputCol("scaledFeatures").fit(inputDf)
    }

    val transformedDf = transformer.transform(inputDf)

    // apply transpose to the output (if applied for the input df)
    if (transposeNeeded) {
      val newDf = transpose(Seq("features", "scaledFeatures"), transformedDf)
      SparkUtil.joinByOrder(data.drop("features"), newDf)
    } else
      transformedDf
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
  ): Traversable[(Q, Double, Double)] = {
    // Fit the model
    val lrModel = estimator.fit(trainData)

    val trainPredictions = lrModel.transform(trainData)
    val testPredictions = lrModel.transform(testData)

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
    test: DataFrame
  ) = {
    def trainAux[MM <: Model[MM]](estimator: Estimator[MM]) =
      train(estimator, metricWithEvaluators, training, test)

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

//    val dataBroadcast = sparkContext.broadcast(data.toSeq)
//    val rdds = sparkContext.parallelize(dataBroadcast.value)
//    val rdds = sparkContext.parallelize(data.toSeq)

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
    val df = session.createDataFrame(data.toSeq, StructType(structTypes))

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

  override def predictRCTimeSeries(
    reservoirSetting: ReservoirLearningSetting,
    topology: Topology,
    predictAhead: Int,
    inputSeries: Seq[Seq[jl.Double]],
    targetSeries: Seq[jl.Double]
  ): Future[RCPredictionResults] = {
    val iterationNum = targetSeries.size - predictAhead - reservoirSetting.getWashoutPeriod

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

    def createIOStream = ioStreamFactory.createInstancePredict(predictAhead, reservoirSetting.getWashoutPeriod)(inputSeries, targetSeries)

    val call = { ioStream: IOStream[jl.Double] =>
      val (predictor, weightAccessor) = reservoirTrainerFactory(initializedTopology, reservoirSetting, ioStream, iterationNum)

      predictor.train(iterationNum)
      val outputs = predictor.outputs
      val desiredOutputs = (ioStream.outputStream take outputs.size).toList.map(_.head)

      val squares = ErrorMeasures.calcSquares(outputs, desiredOutputs)
      val samps = ErrorMeasures.calcSamps(outputs, desiredOutputs)

      val weights: Seq[jl.Double] = {
        for {
          reservoirNode <- reservoirNodes;
          outputNode <- outputNodes
        } yield
          weightAccessor.getWeight(reservoirNode, outputNode)
      }.flatten

      val targetVariance = MathUtil.calcStats(0, targetSeries).getVariance.toDouble

      RCPredictionResults(squares, samps, outputs.toSeq, desiredOutputs, weights, targetVariance)
    }

    Future {
      val ioStream = createIOStream
      call(ioStream)
    }
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
  def trainingTestingResults: Traversable[(Double, Double)]
}

case class ClassificationPerformance (
  val evalMetric: ClassificationEvalMetric.Value,
  val trainingTestingResults: Traversable[(Double, Double)]
) extends Performance {
  override type T = ClassificationEvalMetric.Value
}

case class RegressionPerformance(
  val evalMetric: RegressionEvalMetric.Value,
  val trainingTestingResults: Traversable[(Double, Double)]
) extends Performance {
  override type T = RegressionEvalMetric.Value
}