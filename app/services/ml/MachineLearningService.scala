package services.ml

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{AdaException, Field, FieldTypeId, FieldTypeSpec}
import models.ml.classification.Classification
import models.ml.regression.Regression
import models.ml.unsupervised.UnsupervisedLearning
import models.ml.{ClassificationEvalMetric, _}
import org.apache.spark.ml.feature._
import org.apache.spark.ml._
import org.apache.spark.sql.types.{Metadata, MetadataBuilder, StructType, _}
import org.apache.spark.sql.{DataFrame, Dataset, Row}
import org.apache.spark.ml.clustering._
import org.apache.spark.ml.linalg.{DenseVector, Vector, Vectors}
import org.apache.spark.ml.param._
import org.apache.spark.sql.functions._
import play.api.libs.json.{JsObject, Json}
import services.SparkApp
import play.api.{Configuration, Logger}
import services.stats.StatsService
import org.incal.spark_ml.transformers._
import org.incal.spark_ml.models.ReservoirSpec
import org.incal.core.VectorScalerType

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import services.ml.results._

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

  def classifyTimeSeries(
    data: JsObject,
    ioSpec: IOJsonTimeSeriesSpec,
    predictAhead: Int,
    windowSize: Option[Int],
    reservoirSetting: Option[ReservoirSpec],
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value] = LearningSetting[ClassificationEvalMetric.Value](),
    minCrossValidationTrainingSize: Option[Double],
    replicationData: Option[JsObject] = None,
    binCurvesNumBins: Option[Int] = None
  ): Future[ClassificationResultsHolder]

  def classifyRowTimeSeries(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    orderFieldName: String,
    orderedValues: Seq[Any],
    predictAhead: Int,
    windowSize: Option[Int],
    reservoirSetting: Option[ReservoirSpec],
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value] = LearningSetting[ClassificationEvalMetric.Value](),
    minCrossValidationTrainingSize: Option[Double] = None,
    replicationData: Traversable[JsObject] = Nil,
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

  def regressTimeSeries(
    data: JsObject,
    ioSpec: IOJsonTimeSeriesSpec,
    predictAhead: Int,
    windowSize: Option[Int],
    reservoirSetting: Option[ReservoirSpec],
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value] = LearningSetting[RegressionEvalMetric.Value](),
    minCrossValidationTrainingSize: Option[Double] = None,
    replicationData: Option[JsObject] = None
  ): Future[RegressionResultsHolder]

  def regressRowTimeSeries(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    inputFieldNames: Seq[String],
    outputFieldName: String,
    orderFieldName: String,
    orderedValues: Seq[Any],
    predictAhead: Int,
    windowSize: Option[Int],
    reservoirSetting: Option[ReservoirSpec],
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value] = LearningSetting[RegressionEvalMetric.Value](),
    minCrossValidationTrainingSize: Option[Double] = None,
    replicationData: Traversable[JsObject] = Nil
  ): Future[RegressionResultsHolder]

  def cluster(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorScalerType.Value],
    pcaDim: Option[Int] = None
  ): Traversable[(String, Int)]

  def clusterDf(
    dataFrame: DataFrame,
    idColumnName: String,
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorScalerType.Value],
    pcaDim: Option[Int] = None
  ): Traversable[(String, Int)]

  def clusterAndGetPCA12(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorScalerType.Value],
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
    statsService: StatsService,
    val rcStatesWindowFactory: RCStatesWindowFactory
  ) extends MachineLearningService with SparkMLService {

  override val setting = SparKMLServiceSetting(
    repetitionParallelism = configuration.getInt("ml.repetition_parallelism"),
    binaryClassifierInputName = configuration.getString("ml.binary_classifier.input"),
    useConsecutiveOrderForDL = configuration.getBoolean("ml.dl_use_consecutive_order_transformers")
  )

  private val session = sparkApp.session
  private implicit val sqlContext = sparkApp.sqlContext

  override def classify(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value],
    replicationData: Traversable[JsObject],
    binCurvesNumBins: Option[Int]
  ): Future[ClassificationResultsHolder] = {
    // create training-test and replication data

    // aux function to create a data frame
    def crateDataFrame(jsons: Traversable[JsObject]) = {
      val df = FeaturesDataFrameFactory(session, jsons, fields, Some(outputFieldName))
      BooleanLabelIndexer(Some("labelString")).transform(df)
    }

    // create a training/test data frame with all the features
    val df = crateDataFrame(data)

    // create a replication data frame with all the features
    val replicationDf = if (replicationData.nonEmpty) Some(crateDataFrame(replicationData)) else None

    // run classification with the newly created data frames
    classify(df, replicationDf, mlModel, setting, binCurvesNumBins)
  }

  override def classifyTimeSeries(
    data: JsObject,
    ioSpec: IOJsonTimeSeriesSpec,
    predictAhead: Int,
    windowSize: Option[Int],
    reservoirSetting: Option[ReservoirSpec],
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value],
    minCrossValidationTrainingSize: Option[Double],
    replicationData: Option[JsObject],
    binCurvesNumBins: Option[Int]
  ): Future[ClassificationResultsHolder] = {
    // create training-test and replication data

    // aux function to create a data frame
    def crateDataFrame(json: JsObject) = {
      val df = FeaturesDataFrameFactory.applySeries(session)(json, ioSpec, seriesOrderCol)
      BooleanLabelIndexer(Some("labelString")).transform(df)
    }

    // create a training/test data frame with all the features
    val df = crateDataFrame(data)

    // create a replication data frame with all the features
    val replicationDf = replicationData.map(crateDataFrame)

    // run time-series classification with the newly created data frames
    classifyTimeSeries(df, replicationDf, predictAhead, windowSize, reservoirSetting, mlModel, setting, minCrossValidationTrainingSize, binCurvesNumBins)
  }

  override def classifyRowTimeSeries(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    orderFieldName: String,
    orderedValues: Seq[Any],
    predictAhead: Int,
    windowSize: Option[Int],
    reservoirSetting: Option[ReservoirSpec],
    mlModel: Classification,
    setting: LearningSetting[ClassificationEvalMetric.Value],
    minCrossValidationTrainingSize: Option[Double],
    replicationData: Traversable[JsObject],
    binCurvesNumBins: Option[Int]
  ): Future[ClassificationResultsHolder] = {

    // mapping between an order value and index
    val orderValueIndexFun = mapValuesUDF(orderedValues.zipWithIndex.toMap)

    // create training-test and replication data

    // aux function to create a data frame
    def crateDataFrame(jsons: Traversable[JsObject]) = {
      val df = FeaturesDataFrameFactory(session, jsons, fields, Some(outputFieldName))
      val df2 = BooleanLabelIndexer(Some("labelString")).transform(df)
      df2.withColumn(seriesOrderCol, orderValueIndexFun(df2(orderFieldName)))
    }

    // create a training/test data frame with all the features
    val df = crateDataFrame(data)

    // create a replication data frame with all the features
    val replicationDf = if (replicationData.nonEmpty) Some(crateDataFrame(replicationData)) else None

    // run time-series classification with the newly created data frames
    classifyTimeSeries(df, replicationDf, predictAhead, windowSize, reservoirSetting, mlModel, setting, minCrossValidationTrainingSize, binCurvesNumBins)
  }

  private def mapValuesUDF(map: Map[Any, Int]) = udf { value: Any =>
    map.get(value).getOrElse(
      throw new IllegalStateException(s"The map $map does not contain a value $value.")
    )
  }

  override def regress(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: String,
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value],
    replicationData: Traversable[JsObject]
  ): Future[RegressionResultsHolder] = {
    // create training-test and replication data

    // aux function to create a data frame
    def crateDataFrame(jsons: Traversable[JsObject]) =
      FeaturesDataFrameFactory(session, jsons, fields, Some(outputFieldName))

    // create a training/test data frame with all the features
    val df = crateDataFrame(data)

    // create a replication data frame with all the features
    val replicationDf = if (replicationData.nonEmpty) Some(crateDataFrame(replicationData)) else None

    // run regression with the newly created data frames
    regress(df, replicationDf, mlModel, setting)
  }

  override def regressTimeSeries(
    data: JsObject,
    ioSpec: IOJsonTimeSeriesSpec,
    predictAhead: Int,
    windowSize: Option[Int],
    reservoirSetting: Option[ReservoirSpec],
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value],
    minCrossValidationTrainingSize: Option[Double],
    replicationData: Option[JsObject] = None
  ): Future[RegressionResultsHolder] = {
    // create training-test and replication data

    // aux function to create a data frame
    def crateDataFrame(json: JsObject) = FeaturesDataFrameFactory.applySeries(session)(json, ioSpec, seriesOrderCol)

    // create a training/test data frame with all the features
    val df = crateDataFrame(data)

    // create a replication data frame with all the features
    val replicationDf = replicationData.map(crateDataFrame)

    // run time-series regression with the newly created data frames
    regressTimeSeries(df, replicationDf, predictAhead, windowSize, reservoirSetting, mlModel, setting, minCrossValidationTrainingSize)
  }

  override def regressRowTimeSeries(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    inputFieldNames: Seq[String],
    outputFieldName: String,
    orderFieldName: String,
    orderedValues: Seq[Any],
    predictAhead: Int,
    windowSize: Option[Int],
    reservoirSetting: Option[ReservoirSpec],
    mlModel: Regression,
    setting: LearningSetting[RegressionEvalMetric.Value],
    minCrossValidationTrainingSize: Option[Double],
    replicationData: Traversable[JsObject]
  ): Future[RegressionResultsHolder] = {
    // mapping between an order value and index
    val orderValueIndexFun = mapValuesUDF(orderedValues.zipWithIndex.toMap)

    // create training-test and replication data

    // aux function to create a data frame
    def crateDataFrame(jsons: Traversable[JsObject]) = {
      val df = FeaturesDataFrameFactory(session, jsons, fields)
      val featuresDf = FeaturesDataFrameFactory.prepFeaturesDataFrame(inputFieldNames.toSet, Some(outputFieldName))(df)

      featuresDf.withColumn(seriesOrderCol, orderValueIndexFun(featuresDf(orderFieldName)))
    }

    // create a training/test data frame with all the features
    val df = crateDataFrame(data)

    df.show()

    // create a replication data frame with all the features
    val replicationDf = if (replicationData.nonEmpty) Some(crateDataFrame(replicationData)) else None

    // run time-series regression with the newly created data frames
    regressTimeSeries(df, replicationDf, predictAhead, windowSize, reservoirSetting, mlModel, setting, minCrossValidationTrainingSize)
  }

  override def cluster(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorScalerType.Value],
    pcaDim: Option[Int]
  ): Traversable[(String, Int)] = {
    val (df, idClusters) = clusterAux(data, fields, mlModel, featuresNormalizationType, pcaDim)
    idClusters
  }

  override def clusterAndGetPCA12(
    data: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    mlModel: UnsupervisedLearning,
    featuresNormalizationType: Option[VectorScalerType.Value],
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
    featuresNormalizationType: Option[VectorScalerType.Value],
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
    featuresNormalizationType: Option[VectorScalerType.Value],
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
    featuresNormalizationType: Option[VectorScalerType.Value],
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