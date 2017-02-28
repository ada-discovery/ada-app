package models.ml

import models.ml.GeneralizedLinearRegressionFamily.Value
import models.ml.RegressionSolver.Value

object LogisticModelFamily extends Enumeration {
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
  threshold: Option[Double] = None,
  aggregationDepth: Option[Int] = None
)

object RegressionSolver extends Enumeration {
  val LBFGS = Value("l-bfgs")
  val Normal = Value("normal")
}

case class LinearRegression(
  regularization: Option[Double] = None,
  elasticMixingRatio: Option[Double] = None,
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  fitIntercept: Option[Boolean] = None,
  solver: Option[RegressionSolver.Value] = None,
  standardization: Option[Boolean] = None,
  aggregationDepth: Option[Int] = None
)

object GeneralizedLinearRegressionFamily extends Enumeration {
  val Gaussian = Value("gaussian")
  val Binomial = Value("binomial")
  val Poisson = Value("poisson")
  val Gamma = Value("gamma")
}

object GeneralizedLinearRegressionLinkType extends Enumeration {
  val Identity = Value("identity")
  val Log = Value("log")
  val Logit = Value("logit")
  val Probit = Value("probit")
  val CLogLog = Value("cloglog")
  val Sqrt = Value("sqrt")
  val Inverse = Value("inverse")
}

object GeneralizedLinearRegressionSolver extends Enumeration {
  val IRLS = Value("irls")
}

case class GeneralizedLinearRegression(
  regularization: Option[Double] = None,
  link: Option[GeneralizedLinearRegressionLinkType.Value] = None,
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  fitIntercept: Option[Boolean] = None,
  family: Option[GeneralizedLinearRegressionFamily.Value] = None,
  solver: Option[GeneralizedLinearRegressionSolver.Value] = None
)

object Solver extends Enumeration {
  val LBFGS = Value("l-bfgs")
  val GD = Value("gd")
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

case class RegressionTree(
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

case class RandomRegressionForest(
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