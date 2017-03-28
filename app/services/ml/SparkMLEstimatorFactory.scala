package services.ml

import models.ml.classification.{Classification, DecisionTree, GradientBoostTree, LogisticRegression, MultiLayerPerceptron, NaiveBayes, RandomForest}
import org.apache.spark.ml.{Estimator, Model}
import org.apache.spark.ml.classification.{LogisticRegression => LogisticRegressionClassifier, NaiveBayes => NaiveBayesClassifier, _}
import models.ml.regression.{Regression, GeneralizedLinearRegression => GeneralizedLinearRegressionDef, GradientBoostRegressionTree => GradientBoostRegressionTreeDef, LinearRegression => LinearRegressionDef, RandomRegressionForest => RandomRegressionForestDef, RegressionTree => RegressionTreeDef}
import org.apache.spark.ml.regression.{DecisionTreeRegressor, GBTRegressor, RandomForestRegressor, GeneralizedLinearRegression => GeneralizedLinearRegressor, LinearRegression => LinearRegressor}

object SparkMLEstimatorFactory {

  def apply[M <: Model[M]](model: Classification): Estimator[M] =
    model match {
      case x: LogisticRegression => applyAux(x).asInstanceOf[Estimator[M]]
      case x: MultiLayerPerceptron => applyAux(x).asInstanceOf[Estimator[M]]
      case x: DecisionTree => applyAux(x).asInstanceOf[Estimator[M]]
      case x: RandomForest => applyAux(x).asInstanceOf[Estimator[M]]
      case x: GradientBoostTree => applyAux(x).asInstanceOf[Estimator[M]]
      case x: NaiveBayes => applyAux(x).asInstanceOf[Estimator[M]]
    }

  def apply[M <: Model[M]](model: Regression): Estimator[M] =
    model match {
      case x: LinearRegressionDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: GeneralizedLinearRegressionDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: RegressionTreeDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: RandomRegressionForestDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: GradientBoostRegressionTreeDef => applyAux(x).asInstanceOf[Estimator[M]]
    }

  private def applyAux(model: LogisticRegression): LogisticRegressionClassifier = {
    def set[T] = setSourceParam[T, LogisticRegression, LogisticRegressionClassifier](model)_

    chain(
      set(_.aggregationDepth, _.setAggregationDepth),
      set(_.elasticMixingRatio, _.setElasticNetParam),
      set(_.family.map(_.toString), _.setFamily),
      set(_.fitIntercept, _.setFitIntercept),
      set(_.maxIteration, _.setMaxIter),
      set(_.regularization, _.setRegParam),
      set(_.threshold, _.setThreshold),
      set(_.thresholds.map(_.toArray), _.setThresholds),
      set(_.standardization, _.setStandardization),
      set(_.tolerance, _.setTol)
    )(new LogisticRegressionClassifier())
  }

  private def applyAux(model: MultiLayerPerceptron): MultilayerPerceptronClassifier = {
    def set[T] = setSourceParam[T, MultiLayerPerceptron, MultilayerPerceptronClassifier](model)_

    chain(
      set(_.blockSize, _.setBlockSize),
      set(_.seed, _.setSeed),
      set(_.maxIteration, _.setMaxIter),
      set(_.solver.map(_.toString), _.setSolver),
      set(_.stepSize, _.setStepSize),
      set(_.tolerance, _.setTol),
      set(o => Some(o.layers.toArray), _.setLayers)
    )(new MultilayerPerceptronClassifier())
  }

  private def applyAux(model: DecisionTree): DecisionTreeClassifier = {
    def set[T] = setSourceParam[T, DecisionTree, DecisionTreeClassifier](model)_

    chain(
      set(_.maxDepth, _.setMaxDepth),
      set(_.maxBins, _.setMaxBins),
      set(_.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.minInfoGain, _.setMinInfoGain),
      set(_.seed, _.setSeed),
      set(_.impurity.map(_.toString), _.setImpurity)
    )(new DecisionTreeClassifier())
  }

  private def applyAux(model: RandomForest): RandomForestClassifier = {
    def set[T] = setSourceParam[T, RandomForest, RandomForestClassifier](model)_

    chain(
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
  }

  private def applyAux(model: GradientBoostTree): GBTClassifier = {
    def set[T] = setSourceParam[T, GradientBoostTree, GBTClassifier](model)_

    chain(
      set(_.lossType.map(_.toString), _.setLossType),
      set(_.maxIteration, _.setMaxIter),
      set(_.stepSize, _.setStepSize),
      set(_.maxDepth, _.setMaxDepth),
      set(_.maxBins, _.setMaxBins),
      set(_.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.minInfoGain, _.setMinInfoGain),
      set(_.seed, _.setSeed),
      set(_.subsamplingRate, _.setSubsamplingRate)
//      set(_.impurity.map(_.toString), _.setImpurity)
    )(new GBTClassifier())
  }

  private def applyAux(model: NaiveBayes): NaiveBayesClassifier = {
    def set[T] = setSourceParam[T, NaiveBayes, NaiveBayesClassifier](model)_

    chain(
      set(_.smoothing, _.setSmoothing),
      set(_.modelType.map(_.toString), _.setModelType)
    )(new NaiveBayesClassifier())
  }

  private def applyAux(model: LinearRegressionDef): LinearRegressor = {
    def set[T] = setSourceParam[T, LinearRegressionDef, LinearRegressor](model)_

    chain(
      set(_.aggregationDepth, _.setAggregationDepth),
      set(_.elasticMixingRatio, _.setElasticNetParam),
      set(_.solver.map(_.toString), _.setSolver),
      set(_.fitIntercept, _.setFitIntercept),
      set(_.maxIteration, _.setMaxIter),
      set(_.regularization, _.setRegParam),
      set(_.standardization, _.setStandardization),
      set(_.tolerance, _.setTol)
    )(new LinearRegressor())
  }

  private def applyAux(model: GeneralizedLinearRegressionDef): GeneralizedLinearRegressor = {
    def set[T] = setSourceParam[T, GeneralizedLinearRegressionDef, GeneralizedLinearRegressor](model)_

    chain(
      set(_.solver.map(_.toString), _.setSolver),
      set(_.family.map(_.toString), _.setFamily),
      set(_.fitIntercept, _.setFitIntercept),
      set(_.link.map(_.toString), _.setLink),
      set(_.maxIteration, _.setMaxIter),
      set(_.regularization, _.setRegParam),
      set(_.tolerance, _.setTol)
    )(new GeneralizedLinearRegressor())
  }

  private def applyAux(model: RegressionTreeDef): DecisionTreeRegressor = {
    def set[T] = setSourceParam[T, RegressionTreeDef, DecisionTreeRegressor](model)_

    chain(
      set(_.maxDepth, _.setMaxDepth),
      set(_.maxBins, _.setMaxBins),
      set(_.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.minInfoGain, _.setMinInfoGain),
      set(_.seed, _.setSeed),
      set(_.impurity.map(_.toString), _.setImpurity)
    )(new DecisionTreeRegressor())
  }

  private def applyAux(model: RandomRegressionForestDef): RandomForestRegressor = {
    def set[T] = setSourceParam[T, RandomRegressionForestDef, RandomForestRegressor](model)_

    chain(
      set(_.impurity.map(_.toString), _.setImpurity),
      set(_.numTrees, _.setNumTrees),
      set(_.maxDepth, _.setMaxDepth),
      set(_.maxBins, _.setMaxBins),
      set(_.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.minInfoGain, _.setMinInfoGain),
      set(_.seed, _.setSeed),
      set(_.subsamplingRate, _.setSubsamplingRate),
      set(_.featureSubsetStrategy.map(_.toString), _.setFeatureSubsetStrategy)
    )(new RandomForestRegressor())
  }

  private def applyAux(model: GradientBoostRegressionTreeDef): GBTRegressor = {
    def set[T] = setSourceParam[T, GradientBoostRegressionTreeDef, GBTRegressor](model)_

    chain(
      set(_.lossType.map(_.toString), _.setLossType),
      set(_.maxIteration, _.setMaxIter),
      set(_.stepSize, _.setStepSize),
      set(_.maxDepth, _.setMaxDepth),
      set(_.maxBins, _.setMaxBins),
      set(_.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.minInfoGain, _.setMinInfoGain),
      set(_.seed, _.setSeed),
      set(_.subsamplingRate, _.setSubsamplingRate)
      //      set(_.impurity.map(_.toString), _.setImpurity)
    )(new GBTRegressor())
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
}