package services.ml

import models.AdaException
import models.ml.classification.{Classification, DecisionTree, GradientBoostTree, LinearSupportVectorMachine, LogisticRegression, MultiLayerPerceptron, NaiveBayes, RandomForest}
import models.ml.regression.{Regression, GeneralizedLinearRegression => GeneralizedLinearRegressionDef, GradientBoostRegressionTree => GradientBoostRegressionTreeDef, LinearRegression => LinearRegressionDef, RandomRegressionForest => RandomRegressionForestDef, RegressionTree => RegressionTreeDef}
import models.ml.unsupervised.{UnsupervisedLearning, BisectingKMeans => BisectingKMeansDef, GaussianMixture => GaussianMixtureDef, KMeans => KMeansDef, LDA => LDADef}
import org.apache.spark.ml.{Estimator, Model}
import org.apache.spark.ml.classification.{LogisticRegression => LogisticRegressionClassifier, NaiveBayes => NaiveBayesClassifier, _}
import org.apache.spark.ml.regression.{DecisionTreeRegressor, GBTRegressor, RandomForestRegressor, GeneralizedLinearRegression => GeneralizedLinearRegressor, LinearRegression => LinearRegressor}
import org.apache.spark.ml.clustering.{BisectingKMeans, GaussianMixture, KMeans, LDA}
import org.apache.spark.ml.param._
import org.apache.spark.ml.tuning.ParamGridBuilder
import models.ml.classification.ValueOrSeq._

import scala.collection.mutable.{Buffer, ListBuffer}

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

  private def applyAux(model: LogisticRegression): (LogisticRegressionClassifier, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, LogisticRegression, LogisticRegressionClassifier](model)_

    val estimator1 = chain(
      set(x => toValue(x.aggregationDepth), _.setAggregationDepth),
      set(x => toValue(x.elasticMixingRatio), _.setElasticNetParam),
      set(_.family.map(_.toString), _.setFamily),
      set(_.fitIntercept, _.setFitIntercept),
      set(x => toValue(x.maxIteration), _.setMaxIter),
      set(x => toValue(x.regularization), _.setRegParam),
      set(x => toValue(x.threshold), _.setThreshold),
      set(_.thresholds.map(_.toArray), _.setThresholds),
      set(_.standardization, _.setStandardization),
      set(x => toValue(x.tolerance), _.setTol)
    )(new LogisticRegressionClassifier())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new LogisticRegressionClassifier())
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

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(
    model: MultiLayerPerceptron,
    inputSize: Int,
    outputSize: Int
  ): (MultilayerPerceptronClassifier, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, MultiLayerPerceptron, MultilayerPerceptronClassifier](model)_

    val layers = (Seq(inputSize) ++ model.hiddenLayers ++ Seq(outputSize)).toArray

    val estimator1 = chain(
      set(x => toValue(x.blockSize), _.setBlockSize),
      set(_.seed, _.setSeed),
      set(x => toValue(x.maxIteration), _.setMaxIter),
      set(_.solver.map(_.toString), _.setSolver),
      set(x => toValue(x.stepSize), _.setStepSize),
      set(x => toValue(x.tolerance), _.setTol),
      set(o => Some(layers), _.setLayers)
    )(new MultilayerPerceptronClassifier())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new MultilayerPerceptronClassifier())
      .bind2(_.blockSize, "blockSize")
      .bind(_.seed, "seed")
      .bind2(_.maxIteration, "maxIter")
      .bind(_.solver.map(_.toString), "solver")
      .bind2(_.stepSize, "stepSize")
      .bind2(_.tolerance, "tol")
      .bind(o => Some(layers), "layers")
      .build

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: DecisionTree): (DecisionTreeClassifier, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, DecisionTree, DecisionTreeClassifier](model)_

    val estimator1 = chain(
      set(x => toValue(x.core.maxDepth), _.setMaxDepth),
      set(x => toValue(x.core.maxBins), _.setMaxBins),
      set(x => toValue(x.core.minInstancesPerNode), _.setMinInstancesPerNode),
      set(x => toValue(x.core.minInfoGain), _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(_.impurity.map(_.toString), _.setImpurity)
    )(new DecisionTreeClassifier())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new DecisionTreeClassifier())
      .bind2(_.core.maxDepth, "maxDepth")
      .bind2(_.core.maxBins, "maxBins")
      .bind2(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind2(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind(_.impurity.map(_.toString), "impurity")
      .build

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: RandomForest): (RandomForestClassifier, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, RandomForest, RandomForestClassifier](model)_

    val estimator1 = chain(
      set(x => toValue(x.numTrees), _.setNumTrees),
      set(x => toValue(x.core.maxDepth), _.setMaxDepth),
      set(x => toValue(x.core.maxBins), _.setMaxBins),
      set(x => toValue(x.core.minInstancesPerNode), _.setMinInstancesPerNode),
      set(x => toValue(x.core.minInfoGain), _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(x => toValue(x.subsamplingRate), _.setSubsamplingRate),
      set(_.impurity.map(_.toString), _.setImpurity),
      set(_.featureSubsetStrategy.map(_.toString), _.setFeatureSubsetStrategy)
    )(new RandomForestClassifier())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new RandomForestClassifier())
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

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: GradientBoostTree): (GBTClassifier, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, GradientBoostTree, GBTClassifier](model)_

    val estimator1 = chain(
      set(_.lossType.map(_.toString), _.setLossType),
      set(x => toValue(x.maxIteration), _.setMaxIter),
      set(x => toValue(x.stepSize), _.setStepSize),
      set(x => toValue(x.core.maxDepth), _.setMaxDepth),
      set(x => toValue(x.core.maxBins), _.setMaxBins),
      set(x => toValue(x.core.minInstancesPerNode), _.setMinInstancesPerNode),
      set(x => toValue(x.core.minInfoGain), _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(x => toValue(x.subsamplingRate), _.setSubsamplingRate)
//      set(_.impurity.map(_.toString), _.setImpurity)
    )(new GBTClassifier())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new GBTClassifier())
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

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: NaiveBayes): (NaiveBayesClassifier, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, NaiveBayes, NaiveBayesClassifier](model)_

    val estimator1 = chain(
      set(x => toValue(x.smoothing), _.setSmoothing),
      set(_.modelType.map(_.toString), _.setModelType)
    )(new NaiveBayesClassifier())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new NaiveBayesClassifier())
      .bind2(_.smoothing, "smoothing")
      .bind(_.modelType.map(_.toString), "modelType")
      .build

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: LinearSupportVectorMachine): (LinearSVC, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, LinearSupportVectorMachine, LinearSVC](model)_

    val estimator1 = chain(
      set(x => toValue(x.aggregationDepth), _.setAggregationDepth),
      set(_.fitIntercept, _.setFitIntercept),
      set(x => toValue(x.maxIteration), _.setMaxIter),
      set(x => toValue(x.regularization), _.setRegParam),
      set(_.standardization, _.setStandardization),
      set(x => toValue(x.threshold), _.setThreshold),
      set(x => toValue(x.tolerance), _.setTol)
    )(new LinearSVC())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new LinearSVC())
      .bind2(_.aggregationDepth, "aggregationDepth")
      .bind(_.fitIntercept, "fitIntercept")
      .bind2(_.maxIteration, "maxIter")
      .bind2(_.regularization, "regParam")
      .bind(_.standardization, "standardization")
      .bind2(_.threshold, "threshold")
      .bind2(_.tolerance, "tol")
      .build

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: LinearRegressionDef): (LinearRegressor, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, LinearRegressionDef, LinearRegressor](model)_

    val estimator1 = chain(
      set(x => toValue(x.aggregationDepth), _.setAggregationDepth),
      set(x => toValue(x.elasticMixingRatio), _.setElasticNetParam),
      set(_.solver.map(_.toString), _.setSolver),
      set(_.fitIntercept, _.setFitIntercept),
      set(x => toValue(x.maxIteration), _.setMaxIter),
      set(x => toValue(x.regularization), _.setRegParam),
      set(_.standardization, _.setStandardization),
      set(x => toValue(x.tolerance), _.setTol)
    )(new LinearRegressor())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new LinearRegressor())
      .bind2(_.aggregationDepth, "aggregationDepth")
      .bind2(_.elasticMixingRatio, "elasticNetParam")
      .bind(_.solver.map(_.toString), "solver")
      .bind(_.fitIntercept, "fitIntercept")
      .bind2(_.maxIteration, "maxIter")
      .bind2(_.regularization, "regParam")
      .bind(_.standardization, "standardization")
      .bind2(_.tolerance, "tol")
      .build

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: GeneralizedLinearRegressionDef): (GeneralizedLinearRegressor, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, GeneralizedLinearRegressionDef, GeneralizedLinearRegressor](model)_

    val estimator1 = chain(
      set(_.solver.map(_.toString), _.setSolver),
      set(_.family.map(_.toString), _.setFamily),
      set(_.fitIntercept, _.setFitIntercept),
      set(_.link.map(_.toString), _.setLink),
      set(x => toValue(x.maxIteration), _.setMaxIter),
      set(x => toValue(x.regularization), _.setRegParam),
      set(x => toValue(x.tolerance), _.setTol)
    )(new GeneralizedLinearRegressor())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new GeneralizedLinearRegressor())
      .bind(_.solver.map(_.toString), "solver")
      .bind(_.family.map(_.toString), "family")
      .bind(_.fitIntercept, "fitIntercept")
      .bind(_.link.map(_.toString), "link")
      .bind2(_.maxIteration, "maxIter")
      .bind2(_.regularization, "regParam")
      .bind2(_.tolerance, "tol")
      .build

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: RegressionTreeDef): (DecisionTreeRegressor, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, RegressionTreeDef, DecisionTreeRegressor](model)_

    val estimator1 = chain(
      set(x => toValue(x.core.maxDepth), _.setMaxDepth),
      set(x => toValue(x.core.maxBins), _.setMaxBins),
      set(x => toValue(x.core.minInstancesPerNode), _.setMinInstancesPerNode),
      set(x => toValue(x.core.minInfoGain), _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(_.impurity.map(_.toString), _.setImpurity)
    )(new DecisionTreeRegressor())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new DecisionTreeRegressor())
      .bind2(_.core.maxDepth, "maxDepth")
      .bind2(_.core.maxBins, "maxBins")
      .bind2(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind2(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind(_.impurity.map(_.toString), "impurity")
      .build

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: RandomRegressionForestDef): (RandomForestRegressor, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, RandomRegressionForestDef, RandomForestRegressor](model)_

    val estimator1 = chain(
      set(_.impurity.map(_.toString), _.setImpurity),
      set(x => toValue(x.numTrees), _.setNumTrees),
      set(x => toValue(x.core.maxDepth), _.setMaxDepth),
      set(x => toValue(x.core.maxBins), _.setMaxBins),
      set(x => toValue(x.core.minInstancesPerNode), _.setMinInstancesPerNode),
      set(x => toValue(x.core.minInfoGain), _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(x => toValue(x.subsamplingRate), _.setSubsamplingRate),
      set(_.featureSubsetStrategy.map(_.toString), _.setFeatureSubsetStrategy)
    )(new RandomForestRegressor())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new RandomForestRegressor())
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

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: GradientBoostRegressionTreeDef): (GBTRegressor, Array[ParamMap]) = {
    def set[T] = setSourceParam[T, GradientBoostRegressionTreeDef, GBTRegressor](model)_

    val estimator1 = chain(
      set(_.lossType.map(_.toString), _.setLossType),
      set(x => toValue(x.maxIteration), _.setMaxIter),
      set(x => toValue(x.stepSize), _.setStepSize),
      set(x => toValue(x.core.maxDepth), _.setMaxDepth),
      set(x => toValue(x.core.maxBins), _.setMaxBins),
      set(x => toValue(x.core.minInstancesPerNode), _.setMinInstancesPerNode),
      set(x => toValue(x.core.minInfoGain), _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(x => toValue(x.subsamplingRate), _.setSubsamplingRate)
      //      set(_.impurity.map(_.toString), _.setImpurity)
    )(new GBTRegressor())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new GBTRegressor())
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

    compareParamValues(estimator1, estimator2)

    (estimator2, paramMaps)
  }

  private def applyAux(model: KMeansDef): KMeans = {
    def set[T] = setSourceParam[T, KMeansDef, KMeans](model)_

    val estimator1 = chain(
      set(_.initMode.map(_.toString), _.setInitMode),
      set(_.initSteps, _.setInitSteps),
      set({o => Some(o.k)}, _.setK),
      set(_.maxIteration, _.setMaxIter),
      set(_.tolerance, _.setTol),
      set(_.seed, _.setSeed)
    )(new KMeans())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new KMeans())
      .bind(_.initMode.map(_.toString), "initMode")
      .bind(_.initSteps, "initSteps")
      .bind({o => Some(o.k)}, "k")
      .bind(_.maxIteration, "maxIter")
      .bind(_.tolerance, "tol")
      .bind(_.seed, "seed")
      .build

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: LDADef): LDA = {
    def set[T] = setSourceParam[T, LDADef, LDA](model)_

    val estimator1 = chain(
      set(_.checkpointInterval, _.setCheckpointInterval),
      set(_.keepLastCheckpoint, _.setKeepLastCheckpoint),
      set[Array[Double]](_.docConcentration.map(_.toArray), _.setDocConcentration),
      set(_.optimizeDocConcentration, _.setOptimizeDocConcentration),
      set(_.topicConcentration, _.setTopicConcentration),
      set({o => Some(o.k)}, _.setK),
      set(_.learningDecay, _.setLearningDecay),
      set(_.learningOffset, _.setLearningOffset),
      set(_.maxIteration, _.setMaxIter),
      set(_.optimizer.map(_.toString), _.setOptimizer),
      set(_.subsamplingRate, _.setSubsamplingRate),
      set(_.seed, _.setSeed)
    )(new LDA())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new LDA())
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

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: BisectingKMeansDef): BisectingKMeans = {
    def set[T] = setSourceParam[T, BisectingKMeansDef, BisectingKMeans](model)_

    val estimator1 = chain(
      set({o => Some(o.k)}, _.setK),
      set(_.maxIteration, _.setMaxIter),
      set(_.seed, _.setSeed),
      set(_.minDivisibleClusterSize, _.setMinDivisibleClusterSize)
    )(new BisectingKMeans())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new BisectingKMeans())
      .bind({o => Some(o.k)}, "k")
      .bind(_.maxIteration, "maxIter")
      .bind(_.seed, "seed")
      .bind(_.minDivisibleClusterSize, "minDivisibleClusterSize")
      .build

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: GaussianMixtureDef): GaussianMixture = {
    def set[T] = setSourceParam[T, GaussianMixtureDef, GaussianMixture](model)_

    val estimator1 = chain(
      set({o => Some(o.k)}, _.setK),
      set(_.maxIteration, _.setMaxIter),
      set(_.tolerance, _.setTol),
      set(_.seed, _.setSeed)
    )(new GaussianMixture())

    val (estimator2, paramMaps) = ParamSourceBinder(model, new GaussianMixture())
      .bind({o => Some(o.k)}, "k")
      .bind(_.maxIteration, "maxIter")
      .bind(_.tolerance, "tol")
      .bind(_.seed, "seed")
      .build

    compareParamValues(estimator1, estimator2)

    estimator2
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

  case class ParamSourceBinder[S, T <: Params](source: S, model: T) {
    private var paramValueSetters: Buffer[ParamValueSetter[S, _]] = Buffer[ParamValueSetter[S, _]]()

    def bind[T](value: S => Option[T], paramName: String): this.type =
      bindAux({x => Left(value(x))}, model.getParam(paramName))

    def bind2[T](value: S => ValueOrSeq[T], paramName: String): this.type =
      bindAux(value, model.getParam(paramName))

    private def bindAux[T](values: S => Either[Option[T], Iterable[T]], param: Param[T]): this.type = {
      paramValueSetters.append(ParamValueSetter(param, values))
      this
    }

    def build: (T, Array[ParamMap]) = {
      val paramGrid = new ParamGridBuilder()
      paramValueSetters.foreach(_.set(model, paramGrid, source))
      (model, paramGrid.build())
    }
  }

  case class ParamValueSetter[S, T](
    val param: Param[T],
    value: S => (Either[Option[T], Iterable[T]])
  ) {
    def set(
      params: Params,
      paramGridBuilder: ParamGridBuilder,
      source: S
    ): Unit = value(source) match {
      case Left(valueOption) => valueOption.foreach(params.set(param, _))
      case Right(values) => paramGridBuilder.addGrid(param, values)
    }
//    def get: Option[T] = params.get(param)
  }

  private def compareParamValues(params1: Params, params2: Params) = {
    val pars1 = params1.params
    val pars2 = params2.params

    if (pars1.size != pars2.size) {
      throw new AdaException(s"Param size ${pars1.size} is not the same as param size ${pars2.size}.")
    }

    pars1.foreach { par1 =>
      val par2 = params2.getParam(par1.name)

      val val1 = params1.get(par1)
      val val2 = params2.get(par2)

      if (val1.isDefined != val2.isDefined) {
        throw new AdaException(s"Param value defined: ${val1.isDefined} is not the same as param value defined ${val2.isDefined}.")
      }
      if (val1.isDefined && val2.isDefined)
        if (!val1.get.equals(val2.get)) {
          throw new AdaException(s"Param value ${val1} is not the same as param value ${val2}.")
        }
    }
  }
}