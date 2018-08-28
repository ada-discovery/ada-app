package services.ml

import models.ml.classification.{Classification, DecisionTree, GradientBoostTree, LinearSupportVectorMachine, LogisticRegression, MultiLayerPerceptron, NaiveBayes, RandomForest}
import models.ml.regression.{Regression, GeneralizedLinearRegression => GeneralizedLinearRegressionDef, GradientBoostRegressionTree => GradientBoostRegressionTreeDef, LinearRegression => LinearRegressionDef, RandomRegressionForest => RandomRegressionForestDef, RegressionTree => RegressionTreeDef}
import models.ml.unsupervised.{UnsupervisedLearning, BisectingKMeans => BisectingKMeansDef, GaussianMixture => GaussianMixtureDef, KMeans => KMeansDef, LDA => LDADef}
import org.apache.spark.ml.{Estimator, Model}
import org.apache.spark.ml.classification.{LogisticRegression => LogisticRegressionClassifier, NaiveBayes => NaiveBayesClassifier, _}
import org.apache.spark.ml.regression.{DecisionTreeRegressor, GBTRegressor, RandomForestRegressor, GeneralizedLinearRegression => GeneralizedLinearRegressor, LinearRegression => LinearRegressor}
import org.apache.spark.ml.clustering.{BisectingKMeans, GaussianMixture, KMeans, LDA}
import org.apache.spark.ml.param._
import models.ml.classification.ValueOrSeq._

object SparkMLEstimatorFactory {

  def apply[M <: Model[M]](
    model: Classification,
    inputSize: Int,
    outputSize: Int
  ): (Estimator[M], Array[ParamMap]) = {
    val (estimator, paramMaps) = model match {
      case x: LogisticRegression => applyAux(x)
      case x: MultiLayerPerceptron => applyAux(x, inputSize, outputSize)
      case x: DecisionTree => applyAux(x)
      case x: RandomForest => applyAux(x)
      case x: GradientBoostTree => applyAux(x)
      case x: NaiveBayes => applyAux(x)
      case x: LinearSupportVectorMachine => applyAux(x)
    }

    (estimator.asInstanceOf[Estimator[M]], paramMaps)
  }

  def apply[M <: Model[M]](
    model: Regression
  ): (Estimator[M], Array[ParamMap]) = {
    val (estimator, paramMaps) = model match {
      case x: LinearRegressionDef => applyAux(x)
      case x: GeneralizedLinearRegressionDef => applyAux(x)
      case x: RegressionTreeDef => applyAux(x)
      case x: RandomRegressionForestDef => applyAux(x)
      case x: GradientBoostRegressionTreeDef => applyAux(x)
    }

    (estimator.asInstanceOf[Estimator[M]], paramMaps)
  }

  def apply[M <: Model[M]](
    model: UnsupervisedLearning
  ): Estimator[M] =
    model match {
      case x: KMeansDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: LDADef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: BisectingKMeansDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: GaussianMixtureDef => applyAux(x).asInstanceOf[Estimator[M]]
    }

  private def applyAux(
    model: LogisticRegression
  ): (LogisticRegressionClassifier, Array[ParamMap]) =
    ParamSourceBinder(model, new LogisticRegressionClassifier())
      .bind2(_.aggregationDepth, "aggregationDepth")
      .bind2(_.elasticMixingRatio, "elasticNetParam")
      .bind(_.family.map(_.toString), "family")
      .bind(_.fitIntercept, "fitIntercept")
      .bind2(_.maxIteration, "maxIter")
      .bind2(_.regularization, "regParam")
      .bind2(_.threshold, "threshold")
      .bind(_.thresholds.map(_.toArray), "thresholds")
      .bind(_.standardization, "standardization")
      .bind2(_.tolerance, "tol")
      .build

  private def applyAux(
    model: MultiLayerPerceptron,
    inputSize: Int,
    outputSize: Int
  ): (MultilayerPerceptronClassifier, Array[ParamMap]) = {
    val layers = (Seq(inputSize) ++ model.hiddenLayers ++ Seq(outputSize)).toArray

    ParamSourceBinder(model, new MultilayerPerceptronClassifier())
      .bind2(_.blockSize, "blockSize")
      .bind(_.seed, "seed")
      .bind2(_.maxIteration, "maxIter")
      .bind(_.solver.map(_.toString), "solver")
      .bind2(_.stepSize, "stepSize")
      .bind2(_.tolerance, "tol")
      .bind(o => Some(layers), "layers")
      .build
  }

  private def applyAux(
    model: DecisionTree
  ): (DecisionTreeClassifier, Array[ParamMap]) =
    ParamSourceBinder(model, new DecisionTreeClassifier())
      .bind2(_.core.maxDepth, "maxDepth")
      .bind2(_.core.maxBins, "maxBins")
      .bind2(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind2(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind(_.impurity.map(_.toString), "impurity")
      .build

  private def applyAux(
    model: RandomForest
  ): (RandomForestClassifier, Array[ParamMap]) =
    ParamSourceBinder(model, new RandomForestClassifier())
      .bind2(_.numTrees, "numTrees")
      .bind2(_.core.maxDepth, "maxDepth")
      .bind2(_.core.maxBins, "maxBins")
      .bind2(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind2(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind2(_.subsamplingRate, "subsamplingRate")
      .bind(_.impurity.map(_.toString), "impurity")
      .bind(_.featureSubsetStrategy.map(_.toString), "featureSubsetStrategy")
      .build

  private def applyAux(
    model: GradientBoostTree
  ): (GBTClassifier, Array[ParamMap]) =
    ParamSourceBinder(model, new GBTClassifier())
      .bind(_.lossType.map(_.toString), "lossType")
      .bind2(_.maxIteration, "maxIter")
      .bind2(_.stepSize, "stepSize")
      .bind2(_.core.maxDepth, "maxDepth")
      .bind2(_.core.maxBins, "maxBins")
      .bind2(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind2(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind2(_.subsamplingRate, "subsamplingRate")
      //    .bind(_.impurity.map(_.toString), "impurity")
      .build

  private def applyAux(
    model: NaiveBayes
  ): (NaiveBayesClassifier, Array[ParamMap]) =
    ParamSourceBinder(model, new NaiveBayesClassifier())
      .bind2(_.smoothing, "smoothing")
      .bind(_.modelType.map(_.toString), "modelType")
      .build

  private def applyAux(
    model: LinearSupportVectorMachine
  ): (LinearSVC, Array[ParamMap]) =
    ParamSourceBinder(model, new LinearSVC())
      .bind2(_.aggregationDepth, "aggregationDepth")
      .bind(_.fitIntercept, "fitIntercept")
      .bind2(_.maxIteration, "maxIter")
      .bind2(_.regularization, "regParam")
      .bind(_.standardization, "standardization")
      .bind2(_.threshold, "threshold")
      .bind2(_.tolerance, "tol")
      .build

  private def applyAux(
    model: LinearRegressionDef
  ): (LinearRegressor, Array[ParamMap]) =
    ParamSourceBinder(model, new LinearRegressor())
      .bind2(_.aggregationDepth, "aggregationDepth")
      .bind2(_.elasticMixingRatio, "elasticNetParam")
      .bind(_.solver.map(_.toString), "solver")
      .bind(_.fitIntercept, "fitIntercept")
      .bind2(_.maxIteration, "maxIter")
      .bind2(_.regularization, "regParam")
      .bind(_.standardization, "standardization")
      .bind2(_.tolerance, "tol")
      .build

  private def applyAux(
    model: GeneralizedLinearRegressionDef
  ): (GeneralizedLinearRegressor, Array[ParamMap]) =
    ParamSourceBinder(model, new GeneralizedLinearRegressor())
      .bind(_.solver.map(_.toString), "solver")
      .bind(_.family.map(_.toString), "family")
      .bind(_.fitIntercept, "fitIntercept")
      .bind(_.link.map(_.toString), "link")
      .bind2(_.maxIteration, "maxIter")
      .bind2(_.regularization, "regParam")
      .bind2(_.tolerance, "tol")
      .build

  private def applyAux(
    model: RegressionTreeDef
  ): (DecisionTreeRegressor, Array[ParamMap]) =
    ParamSourceBinder(model, new DecisionTreeRegressor())
      .bind2(_.core.maxDepth, "maxDepth")
      .bind2(_.core.maxBins, "maxBins")
      .bind2(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind2(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind(_.impurity.map(_.toString), "impurity")
      .build

  private def applyAux(
    model: RandomRegressionForestDef
  ): (RandomForestRegressor, Array[ParamMap]) =
    ParamSourceBinder(model, new RandomForestRegressor())
      .bind(_.impurity.map(_.toString), "impurity")
      .bind2(_.numTrees, "numTrees")
      .bind2(_.core.maxDepth, "maxDepth")
      .bind2(_.core.maxBins, "maxBins")
      .bind2(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind2(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind2(_.subsamplingRate, "subsamplingRate")
      .bind(_.featureSubsetStrategy.map(_.toString), "featureSubsetStrategy")
      .build

  private def applyAux(
    model: GradientBoostRegressionTreeDef
  ): (GBTRegressor, Array[ParamMap]) =
    ParamSourceBinder(model, new GBTRegressor())
      .bind(_.lossType.map(_.toString), "lossType")
      .bind2(_.maxIteration, "maxIter")
      .bind2(_.stepSize, "stepSize")
      .bind2(_.core.maxDepth, "maxDepth")
      .bind2(_.core.maxBins, "maxBins")
      .bind2(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind2(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind2(_.subsamplingRate, "subsamplingRate")
      //    .bind(_.impurity.map(_.toString), "impurity")
      .build

  private def applyAux(
    model: KMeansDef)
  : KMeans = {
    val (estimator, _) = ParamSourceBinder(model, new KMeans())
      .bind(_.initMode.map(_.toString), "initMode")
      .bind(_.initSteps, "initSteps")
      .bind({o => Some(o.k)}, "k")
      .bind(_.maxIteration, "maxIter")
      .bind(_.tolerance, "tol")
      .bind(_.seed, "seed")
      .build

    estimator
  }

  private def applyAux(
    model: LDADef
  ): LDA = {
    val (estimator, _) = ParamSourceBinder(model, new LDA())
      .bind(_.checkpointInterval, "checkpointInterval")
      .bind(_.keepLastCheckpoint, "keepLastCheckpoint")
      .bind[Array[Double]](_.docConcentration.map(_.toArray), "docConcentration")
      .bind(_.optimizeDocConcentration, "optimizeDocConcentration")
      .bind(_.topicConcentration, "topicConcentration")
      .bind({o => Some(o.k)}, "k")
      .bind(_.learningDecay, "learningDecay")
      .bind(_.learningOffset, "learningOffset")
      .bind(_.maxIteration, "maxIter")
      .bind(_.optimizer.map(_.toString), "optimizer")
      .bind(_.subsamplingRate, "subsamplingRate")
      .bind(_.seed, "seed")
      .build

    estimator
  }

  private def applyAux(
    model: BisectingKMeansDef
  ): BisectingKMeans = {
    val (estimator, _) = ParamSourceBinder(model, new BisectingKMeans())
      .bind({o => Some(o.k)}, "k")
      .bind(_.maxIteration, "maxIter")
      .bind(_.seed, "seed")
      .bind(_.minDivisibleClusterSize, "minDivisibleClusterSize")
      .build

    estimator
  }

  private def applyAux(
    model: GaussianMixtureDef
  ): GaussianMixture = {
    val (estimator, _) = ParamSourceBinder(model, new GaussianMixture())
      .bind({o => Some(o.k)}, "k")
      .bind(_.maxIteration, "maxIter")
      .bind(_.tolerance, "tol")
      .bind(_.seed, "seed")
      .build

    estimator
  }

  // helper functions

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
}