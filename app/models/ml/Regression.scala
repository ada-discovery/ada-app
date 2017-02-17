package models.ml

object ModelFamily extends Enumeration {
  val auto, binomial, multinomial = Value
}

case class Regression(
  regularization: Option[Double] = None,
  elasticMixingRatio: Option[Double] = None,
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  fitIntercept: Option[Boolean] = None,
  family: Option[ModelFamily.Value] = None,
  standardization: Option[Boolean] = None,
  threshold: Option[Double] = None,
  aggregationDepth: Option[Int] = None
)

object Solver extends Enumeration {
  val LBFGS, GD = Value
}

case class MultiLayerPerceptron(
  layers: Seq[Int],
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  blockSize: Option[Int] = None,
  solver: Option[Solver.Value] = None,
  seed: Option[Long] = None,
  stepSize: Option[Double] = None
)

object FeatureSubsetStrategy extends Enumeration {
  val auto, all, onethird, sqrt, log2 = Value
}

object Impurity extends Enumeration {
  val entropy, gini = Value
}

case class DecisionTree(
  maxDepth: Option[Int] = None,
  maxBins: Option[Int] = None,
  minInstancesPerNode: Option[Int] = None,
  minInfoGain: Option[Double] = None,
  seed: Option[Long] = None,
  impurity: Option[Impurity.Value] = None
)

case class RandomForest(
  numTrees: Option[Int] = None,
  maxDepth: Option[Int] = None,
  maxBins: Option[Int] = None,
  minInstancesPerNode: Option[Int] = None,
  minInfoGain: Option[Double] = None,
  seed: Option[Long] = None,
  subsamplingRate: Option[Double] = None,
  impurity: Option[Impurity.Value] = None,
  featureSubsetStrategy: Option[FeatureSubsetStrategy.Value] = None
)

case class GradientBoostTree(
  maxIteration: Option[Int] = None,
  stepSize: Option[Double] = None,
  maxDepth: Option[Int] = None,
  maxBins: Option[Int] = None,
  minInstancesPerNode: Option[Int] = None,
  minInfoGain: Option[Double] = None,
  seed: Option[Long] = None,
  subsamplingRate: Option[Double] = None,
  impurity: Option[Impurity.Value] = None
)

object BayesModelType extends Enumeration {
  val multinomial, bernoulli = Value
}

case class NaiveBayes(
  smoothing: Option[Double] = None,
  modelType: Option[BayesModelType.Value] = None
)