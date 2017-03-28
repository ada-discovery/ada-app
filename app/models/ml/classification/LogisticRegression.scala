package models.ml.classification

abstract class Classification

object LogisticModelFamily extends Enumeration {
  val Auto = Value("auto")
  val Binomial = Value("binomial")
  val Multinomial = Value("multinomial")
}

case class LogisticRegression(
  regularization: Option[Double] = None,
  elasticMixingRatio: Option[Double] = None,
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  fitIntercept: Option[Boolean] = None,
  family: Option[LogisticModelFamily.Value] = None,
  standardization: Option[Boolean] = None,
  aggregationDepth: Option[Int] = None,
  threshold: Option[Double] = None,
  thresholds: Option[Seq[Double]] = None  // used for multinomial logistic regression
) extends Classification

object MLPSolver extends Enumeration {
  val LBFGS = Value("l-bfgs")
  val GD = Value("gd")
}

case class MultiLayerPerceptron(
  layers: Seq[Int],
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  blockSize: Option[Int] = None,
  solver: Option[MLPSolver.Value] = None,
  seed: Option[Long] = None,
  stepSize: Option[Double] = None
) extends Classification

object FeatureSubsetStrategy extends Enumeration {
  val auto, all, onethird, sqrt, log2 = Value
}

object DecisionTreeImpurity extends Enumeration {
  val entropy, gini = Value
}

case class DecisionTree(
  maxDepth: Option[Int] = None,
  maxBins: Option[Int] = None,
  minInstancesPerNode: Option[Int] = None,
  minInfoGain: Option[Double] = None,
  seed: Option[Long] = None,
  impurity: Option[DecisionTreeImpurity.Value] = None
) extends Classification

case class RandomForest(
  numTrees: Option[Int] = None,
  maxDepth: Option[Int] = None,
  maxBins: Option[Int] = None,
  minInstancesPerNode: Option[Int] = None,
  minInfoGain: Option[Double] = None,
  seed: Option[Long] = None,
  subsamplingRate: Option[Double] = None,
  impurity: Option[DecisionTreeImpurity.Value] = None,
  featureSubsetStrategy: Option[FeatureSubsetStrategy.Value] = None
) extends Classification

object GBTClassificationLossType extends Enumeration {
  val logistic = Value
}

case class GradientBoostTree(
  maxIteration: Option[Int] = None,
  stepSize: Option[Double] = None,
  maxDepth: Option[Int] = None,
  maxBins: Option[Int] = None,
  minInstancesPerNode: Option[Int] = None,
  minInfoGain: Option[Double] = None,
  seed: Option[Long] = None,
  subsamplingRate: Option[Double] = None,
  lossType: Option[GBTClassificationLossType.Value] = None
//  impurity: Option[Impurity.Value] = None
) extends Classification

object BayesModelType extends Enumeration {
  val multinomial, bernoulli = Value
}

case class NaiveBayes(
  smoothing: Option[Double] = None,
  modelType: Option[BayesModelType.Value] = None
) extends Classification