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

import scala.collection.mutable.{Buffer, ListBuffer}

object SparkMLEstimatorFactory {

  def apply[M <: Model[M]](
    model: Classification,
    inputSize: Int,
    outputSize: Int
  ): Estimator[M] with Params =
    model match {
      case x: LogisticRegression => applyAux(x).asInstanceOf[Estimator[M]]
      case x: MultiLayerPerceptron => applyAux(x, inputSize, outputSize).asInstanceOf[Estimator[M]]
      case x: DecisionTree => applyAux(x).asInstanceOf[Estimator[M]]
      case x: RandomForest => applyAux(x).asInstanceOf[Estimator[M]]
      case x: GradientBoostTree => applyAux(x).asInstanceOf[Estimator[M]]
      case x: NaiveBayes => applyAux(x).asInstanceOf[Estimator[M]]
      case x: LinearSupportVectorMachine => applyAux(x).asInstanceOf[Estimator[M]]
    }

  def apply[M <: Model[M]](model: Regression): Estimator[M] with Params =
    model match {
      case x: LinearRegressionDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: GeneralizedLinearRegressionDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: RegressionTreeDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: RandomRegressionForestDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: GradientBoostRegressionTreeDef => applyAux(x).asInstanceOf[Estimator[M]]
    }

  def apply[M <: Model[M]](model: UnsupervisedLearning): Estimator[M] with Params =
    model match {
      case x: KMeansDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: LDADef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: BisectingKMeansDef => applyAux(x).asInstanceOf[Estimator[M]]
      case x: GaussianMixtureDef => applyAux(x).asInstanceOf[Estimator[M]]
    }

  private def applyAux(model: LogisticRegression): LogisticRegressionClassifier = {
    def set[T] = setSourceParam[T, LogisticRegression, LogisticRegressionClassifier](model)_

    val estimator1 = chain(
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

    val estimator2 = ParamSourceBinder(model, new LogisticRegressionClassifier())
      .bind(_.aggregationDepth, "aggregationDepth")
      .bind(_.elasticMixingRatio, "elasticNetParam")
      .bind(_.family.map(_.toString), "family")
      .bind(_.fitIntercept, "fitIntercept")
      .bind(_.maxIteration, "maxIter")
      .bind(_.regularization, "regParam")
      .bind(_.threshold, "threshold")
      .bind(_.thresholds.map(_.toArray), "thresholds")
      .bind(_.standardization, "standardization")
      .bind(_.tolerance, "tol")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(
    model: MultiLayerPerceptron,
    inputSize: Int,
    outputSize: Int
  ): MultilayerPerceptronClassifier = {
    def set[T] = setSourceParam[T, MultiLayerPerceptron, MultilayerPerceptronClassifier](model)_

    val estimator1 = chain(
      set(_.blockSize, _.setBlockSize),
      set(_.seed, _.setSeed),
      set(_.maxIteration, _.setMaxIter),
      set(_.solver.map(_.toString), _.setSolver),
      set(_.stepSize, _.setStepSize),
      set(_.tolerance, _.setTol),
      set(o => Some((Seq(inputSize) ++ o.hiddenLayers ++ Seq(outputSize)).toArray), _.setLayers)
    )(new MultilayerPerceptronClassifier())

    val estimator2 = ParamSourceBinder(model, new MultilayerPerceptronClassifier())
      .bind(_.blockSize, "blockSize")
      .bind(_.seed, "seed")
      .bind(_.maxIteration, "maxIter")
      .bind(_.solver.map(_.toString), "solver")
      .bind(_.stepSize, "stepSize")
      .bind(_.tolerance, "tol")
      .bind(o => Some((Seq(inputSize) ++ o.hiddenLayers ++ Seq(outputSize)).toArray), "layers")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: DecisionTree): DecisionTreeClassifier = {
    def set[T] = setSourceParam[T, DecisionTree, DecisionTreeClassifier](model)_

    val estimator1 = chain(
      set(_.core.maxDepth, _.setMaxDepth),
      set(_.core.maxBins, _.setMaxBins),
      set(_.core.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.core.minInfoGain, _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(_.impurity.map(_.toString), _.setImpurity)
    )(new DecisionTreeClassifier())

    val estimator2 = ParamSourceBinder(model, new DecisionTreeClassifier())
      .bind(_.core.maxDepth, "maxDepth")
      .bind(_.core.maxBins, "maxBins")
      .bind(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind(_.impurity.map(_.toString), "impurity")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: RandomForest): RandomForestClassifier = {
    def set[T] = setSourceParam[T, RandomForest, RandomForestClassifier](model)_

    val estimator1 = chain(
      set(_.numTrees, _.setNumTrees),
      set(_.core.maxDepth, _.setMaxDepth),
      set(_.core.maxBins, _.setMaxBins),
      set(_.core.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.core.minInfoGain, _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(_.subsamplingRate, _.setSubsamplingRate),
      set(_.impurity.map(_.toString), _.setImpurity),
      set(_.featureSubsetStrategy.map(_.toString), _.setFeatureSubsetStrategy)
    )(new RandomForestClassifier())

    val estimator2 = ParamSourceBinder(model, new RandomForestClassifier())
      .bind(_.numTrees, "numTrees")
      .bind(_.core.maxDepth, "maxDepth")
      .bind(_.core.maxBins, "maxBins")
      .bind(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind(_.subsamplingRate, "subsamplingRate")
      .bind(_.impurity.map(_.toString), "impurity")
      .bind(_.featureSubsetStrategy.map(_.toString), "featureSubsetStrategy")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: GradientBoostTree): GBTClassifier = {
    def set[T] = setSourceParam[T, GradientBoostTree, GBTClassifier](model)_

    val estimator1 = chain(
      set(_.lossType.map(_.toString), _.setLossType),
      set(_.maxIteration, _.setMaxIter),
      set(_.stepSize, _.setStepSize),
      set(_.core.maxDepth, _.setMaxDepth),
      set(_.core.maxBins, _.setMaxBins),
      set(_.core.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.core.minInfoGain, _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(_.subsamplingRate, _.setSubsamplingRate)
//      set(_.impurity.map(_.toString), _.setImpurity)
    )(new GBTClassifier())

    val estimator2 = ParamSourceBinder(model, new GBTClassifier())
      .bind(_.lossType.map(_.toString), "lossType")
      .bind(_.maxIteration, "maxIter")
      .bind(_.stepSize, "stepSize")
      .bind(_.core.maxDepth, "maxDepth")
      .bind(_.core.maxBins, "maxBins")
      .bind(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind(_.subsamplingRate, "subsamplingRate")
      //    .bind(_.impurity.map(_.toString), "impurity")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: NaiveBayes): NaiveBayesClassifier = {
    def set[T] = setSourceParam[T, NaiveBayes, NaiveBayesClassifier](model)_

    val estimator1 = chain(
      set(_.smoothing, _.setSmoothing),
      set(_.modelType.map(_.toString), _.setModelType)
    )(new NaiveBayesClassifier())

    val estimator2 = ParamSourceBinder(model, new NaiveBayesClassifier())
      .bind(_.smoothing, "smoothing")
      .bind(_.modelType.map(_.toString), "modelType")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: LinearSupportVectorMachine): LinearSVC = {
    def set[T] = setSourceParam[T, LinearSupportVectorMachine, LinearSVC](model)_

    val estimator1 = chain(
      set(_.aggregationDepth, _.setAggregationDepth),
      set(_.fitIntercept, _.setFitIntercept),
      set(_.maxIteration, _.setMaxIter),
      set(_.regularization, _.setRegParam),
      set(_.standardization, _.setStandardization),
      set(_.threshold, _.setThreshold),
      set(_.tolerance, _.setTol)
    )(new LinearSVC())

    val estimator2 = ParamSourceBinder(model, new LinearSVC())
      .bind(_.aggregationDepth, "aggregationDepth")
      .bind(_.fitIntercept, "fitIntercept")
      .bind(_.maxIteration, "maxIter")
      .bind(_.regularization, "regParam")
      .bind(_.standardization, "standardization")
      .bind(_.threshold, "threshold")
      .bind(_.tolerance, "tol")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: LinearRegressionDef): LinearRegressor = {
    def set[T] = setSourceParam[T, LinearRegressionDef, LinearRegressor](model)_

    val estimator1 = chain(
      set(_.aggregationDepth, _.setAggregationDepth),
      set(_.elasticMixingRatio, _.setElasticNetParam),
      set(_.solver.map(_.toString), _.setSolver),
      set(_.fitIntercept, _.setFitIntercept),
      set(_.maxIteration, _.setMaxIter),
      set(_.regularization, _.setRegParam),
      set(_.standardization, _.setStandardization),
      set(_.tolerance, _.setTol)
    )(new LinearRegressor())

    val estimator2 = ParamSourceBinder(model, new LinearRegressor())
      .bind(_.aggregationDepth, "aggregationDepth")
      .bind(_.elasticMixingRatio, "elasticNetParam")
      .bind(_.solver.map(_.toString), "solver")
      .bind(_.fitIntercept, "fitIntercept")
      .bind(_.maxIteration, "maxIter")
      .bind(_.regularization, "regParam")
      .bind(_.standardization, "standardization")
      .bind(_.tolerance, "tol")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: GeneralizedLinearRegressionDef): GeneralizedLinearRegressor = {
    def set[T] = setSourceParam[T, GeneralizedLinearRegressionDef, GeneralizedLinearRegressor](model)_

    val estimator1 = chain(
      set(_.solver.map(_.toString), _.setSolver),
      set(_.family.map(_.toString), _.setFamily),
      set(_.fitIntercept, _.setFitIntercept),
      set(_.link.map(_.toString), _.setLink),
      set(_.maxIteration, _.setMaxIter),
      set(_.regularization, _.setRegParam),
      set(_.tolerance, _.setTol)
    )(new GeneralizedLinearRegressor())

    val estimator2 = ParamSourceBinder(model, new GeneralizedLinearRegressor())
      .bind(_.solver.map(_.toString), "solver")
      .bind(_.family.map(_.toString), "family")
      .bind(_.fitIntercept, "fitIntercept")
      .bind(_.link.map(_.toString), "link")
      .bind(_.maxIteration, "maxIter")
      .bind(_.regularization, "regParam")
      .bind(_.tolerance, "tol")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: RegressionTreeDef): DecisionTreeRegressor = {
    def set[T] = setSourceParam[T, RegressionTreeDef, DecisionTreeRegressor](model)_

    val estimator1 = chain(
      set(_.core.maxDepth, _.setMaxDepth),
      set(_.core.maxBins, _.setMaxBins),
      set(_.core.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.core.minInfoGain, _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(_.impurity.map(_.toString), _.setImpurity)
    )(new DecisionTreeRegressor())

    val estimator2 = ParamSourceBinder(model, new DecisionTreeRegressor())
      .bind(_.core.maxDepth, "maxDepth")
      .bind(_.core.maxBins, "maxBins")
      .bind(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind(_.impurity.map(_.toString), "impurity")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: RandomRegressionForestDef): RandomForestRegressor = {
    def set[T] = setSourceParam[T, RandomRegressionForestDef, RandomForestRegressor](model)_

    val estimator1 = chain(
      set(_.impurity.map(_.toString), _.setImpurity),
      set(_.numTrees, _.setNumTrees),
      set(_.core.maxDepth, _.setMaxDepth),
      set(_.core.maxBins, _.setMaxBins),
      set(_.core.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.core.minInfoGain, _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(_.subsamplingRate, _.setSubsamplingRate),
      set(_.featureSubsetStrategy.map(_.toString), _.setFeatureSubsetStrategy)
    )(new RandomForestRegressor())

    val estimator2 = ParamSourceBinder(model, new RandomForestRegressor())
      .bind(_.impurity.map(_.toString), "impurity")
      .bind(_.numTrees, "numTrees")
      .bind(_.core.maxDepth, "maxDepth")
      .bind(_.core.maxBins, "maxBins")
      .bind(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind(_.subsamplingRate, "subsamplingRate")
      .bind(_.featureSubsetStrategy.map(_.toString), "featureSubsetStrategy")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
  }

  private def applyAux(model: GradientBoostRegressionTreeDef): GBTRegressor = {
    def set[T] = setSourceParam[T, GradientBoostRegressionTreeDef, GBTRegressor](model)_

    val estimator1 = chain(
      set(_.lossType.map(_.toString), _.setLossType),
      set(_.maxIteration, _.setMaxIter),
      set(_.stepSize, _.setStepSize),
      set(_.core.maxDepth, _.setMaxDepth),
      set(_.core.maxBins, _.setMaxBins),
      set(_.core.minInstancesPerNode, _.setMinInstancesPerNode),
      set(_.core.minInfoGain, _.setMinInfoGain),
      set(_.core.seed, _.setSeed),
      set(_.subsamplingRate, _.setSubsamplingRate)
      //      set(_.impurity.map(_.toString), _.setImpurity)
    )(new GBTRegressor())

    val estimator2 = ParamSourceBinder(model, new GBTRegressor())
      .bind(_.lossType.map(_.toString), "lossType")
      .bind(_.maxIteration, "maxIter")
      .bind(_.stepSize, "stepSize")
      .bind(_.core.maxDepth, "maxDepth")
      .bind(_.core.maxBins, "maxBins")
      .bind(_.core.minInstancesPerNode, "minInstancesPerNode")
      .bind(_.core.minInfoGain, "minInfoGain")
      .bind(_.core.seed, "seed")
      .bind(_.subsamplingRate, "subsamplingRate")
      //    .bind(_.impurity.map(_.toString), "impurity")
      .setValuesAndGetModel

    compareParamValues(estimator1, estimator2)

    estimator2
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

    val estimator2 = ParamSourceBinder(model, new KMeans())
      .bind(_.initMode.map(_.toString), "initMode")
      .bind(_.initSteps, "initSteps")
      .bind({o => Some(o.k)}, "k")
      .bind(_.maxIteration, "maxIter")
      .bind(_.tolerance, "tol")
      .bind(_.seed, "seed")
      .setValuesAndGetModel

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

    val estimator2 = ParamSourceBinder(model, new LDA())
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
      .setValuesAndGetModel

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

    val estimator2 = ParamSourceBinder(model, new BisectingKMeans())
      .bind({o => Some(o.k)}, "k")
      .bind(_.maxIteration, "maxIter")
      .bind(_.seed, "seed")
      .bind(_.minDivisibleClusterSize, "minDivisibleClusterSize")
      .setValuesAndGetModel

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

    val estimator2 = ParamSourceBinder(model, new GaussianMixture())
      .bind({o => Some(o.k)}, "k")
      .bind(_.maxIteration, "maxIter")
      .bind(_.tolerance, "tol")
      .bind(_.seed, "seed")
      .setValuesAndGetModel

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

  case class ParamSourceBinder[S, T <: Params](source: S, params: T) {
    private val paramGrid = new ParamGridBuilder()

    private var paramValueSetters: Buffer[ParamValueSetter[S, _]] = Buffer[ParamValueSetter[S, _]]()
    private var paramGridSetters: Buffer[ParamGridSetter[S, _]] = Buffer[ParamGridSetter[S, _]]()

    def bind[T](value: S => Option[T], paramName: String): this.type =
      bindAux(value, params.getParam(paramName))

    private def bindAux[T](value: S => Option[T], param: Param[T]): this.type = {
      paramValueSetters.append(ParamValueSetter(params, source)(param, value))
      this
    }

    private def bindGridAux[T](values: S => Iterable[T], param: Param[T]): this.type = {
      paramGridSetters.append(ParamGridSetter(paramGrid, source)(param, values))
      this
    }

    def setValues: this.type = {
      paramValueSetters.foreach(_.set)
      this
    }

    def setValuesAndGetModel: T = {
      paramValueSetters.foreach(_.set)
      params
    }

    def model: T = params

    def setParamGrid = paramValueSetters.foreach { paramWrapper =>
      paramGrid.addGrid(paramWrapper.param, None)
    }
  }

  case class ParamValueSetter[S, T](
    params: Params,
    source: S)(
    val param: Param[T],
    value: S => Option[T]
  ) {
    def set: Unit = value(source).foreach(params.set(param, _))
//    def get: Option[T] = params.get(param)
  }

  case class ParamGridSetter[S, T](
    paramGridBuilder: ParamGridBuilder,
    source: S)(
    val param: Param[T],
    values: S => Iterable[T]
  ) {
    def set: Unit = paramGridBuilder.addGrid(param, values(source))
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