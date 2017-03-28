package models.ml.regression

abstract class Regression

object RegressionSolver extends Enumeration {
  val Auto = Value("auto")
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
) extends Regression

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
) extends Regression

object FeatureSubsetStrategy extends Enumeration {
  val auto, all, onethird, sqrt, log2 = Value
}

object RegressionTreeImpurity extends Enumeration {
  val variance = Value
}

case class RegressionTree(
  maxDepth: Option[Int] = None,
  maxBins: Option[Int] = None,
  minInstancesPerNode: Option[Int] = None,
  minInfoGain: Option[Double] = None,
  seed: Option[Long] = None,
  impurity: Option[RegressionTreeImpurity.Value] = None
) extends Regression

case class RandomRegressionForest(
  numTrees: Option[Int] = None,
  maxDepth: Option[Int] = None,
  maxBins: Option[Int] = None,
  minInstancesPerNode: Option[Int] = None,
  minInfoGain: Option[Double] = None,
  seed: Option[Long] = None,
  subsamplingRate: Option[Double] = None,
  impurity: Option[RegressionTreeImpurity.Value] = None,
  featureSubsetStrategy: Option[FeatureSubsetStrategy.Value] = None
) extends Regression

object GBTRegressionLossType extends Enumeration {
  val squared, absolute = Value
}

case class GradientBoostRegressionTree(
  maxIteration: Option[Int] = None,
  stepSize: Option[Double] = None,
  maxDepth: Option[Int] = None,
  maxBins: Option[Int] = None,
  minInstancesPerNode: Option[Int] = None,
  minInfoGain: Option[Double] = None,
  seed: Option[Long] = None,
  subsamplingRate: Option[Double] = None,
  lossType: Option[GBTRegressionLossType.Value] = None
//    impurity: Option[Impurity.Value] = None,
) extends Regression