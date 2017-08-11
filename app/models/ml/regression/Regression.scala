package models.ml.regression

import java.util.Date

import dataaccess.BSONObjectIdentity
import models.json.{EnumFormat, ManifestedFormat, SubTypeFormat}
import models.ml.TreeCore
import play.api.libs.json.{Format, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

abstract class Regression {
  val _id: Option[BSONObjectID]
  val name: Option[String]
  val createdById: Option[BSONObjectID]
  val timeCreated: Date
}

object RegressionSolver extends Enumeration {
  val Auto = Value("auto")
  val LBFGS = Value("l-bfgs")
  val Normal = Value("normal")
}

case class LinearRegression(
  _id: Option[BSONObjectID] = None,
  regularization: Option[Double] = None,
  elasticMixingRatio: Option[Double] = None,
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  fitIntercept: Option[Boolean] = None,
  solver: Option[RegressionSolver.Value] = None,
  standardization: Option[Boolean] = None,
  aggregationDepth: Option[Int] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
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
  _id: Option[BSONObjectID] = None,
  regularization: Option[Double] = None,
  link: Option[GeneralizedLinearRegressionLinkType.Value] = None,
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  fitIntercept: Option[Boolean] = None,
  family: Option[GeneralizedLinearRegressionFamily.Value] = None,
  solver: Option[GeneralizedLinearRegressionSolver.Value] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Regression

object RandomRegressionForestFeatureSubsetStrategy extends Enumeration {
  val auto, all, onethird, sqrt, log2 = Value
}

object RegressionTreeImpurity extends Enumeration {
  val variance = Value
}

case class RegressionTree(
  _id: Option[BSONObjectID] = None,
  core: TreeCore = TreeCore(),
  impurity: Option[RegressionTreeImpurity.Value] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Regression

case class RandomRegressionForest(
  _id: Option[BSONObjectID] = None,
  core: TreeCore = TreeCore(),
  numTrees: Option[Int] = None,
  subsamplingRate: Option[Double] = None,
  impurity: Option[RegressionTreeImpurity.Value] = None,
  featureSubsetStrategy: Option[RandomRegressionForestFeatureSubsetStrategy.Value] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Regression

object GBTRegressionLossType extends Enumeration {
  val squared, absolute = Value
}

case class GradientBoostRegressionTree(
  _id: Option[BSONObjectID] = None,
  core: TreeCore = TreeCore(),
  maxIteration: Option[Int] = None,
  stepSize: Option[Double] = None,
  subsamplingRate: Option[Double] = None,
  lossType: Option[GBTRegressionLossType.Value] = None,
//    impurity: Option[Impurity.Value] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Regression

object Regression {
  implicit val regressionSolverEnumTypeFormat = EnumFormat.enumFormat(RegressionSolver)
  implicit val generalizedLinearRegressionFamilyEnumTypeFormat = EnumFormat.enumFormat(GeneralizedLinearRegressionFamily)
  implicit val generalizedLinearRegressionLinkTypeEnumTypeFormat = EnumFormat.enumFormat(GeneralizedLinearRegressionLinkType)
  implicit val generalizedLinearRegressionSolverEnumTypeFormat = EnumFormat.enumFormat(GeneralizedLinearRegressionSolver)
  implicit val featureSubsetStrategyEnumTypeFormat = EnumFormat.enumFormat(RandomRegressionForestFeatureSubsetStrategy)
  implicit val regressionTreeImpurityEnumTypeFormat = EnumFormat.enumFormat(RegressionTreeImpurity)
  implicit val gbtRegressionLossTypeEnumTypeFormat = EnumFormat.enumFormat(GBTRegressionLossType)

  private implicit val treeCoreFormat = Json.format[TreeCore]

  implicit val regressionFormat: Format[Regression] = new SubTypeFormat[Regression](
    Seq(
      ManifestedFormat(Json.format[LinearRegression]),
      ManifestedFormat(Json.format[GeneralizedLinearRegression]),
      ManifestedFormat(Json.format[RegressionTree]),
      ManifestedFormat(Json.format[RandomRegressionForest]),
      ManifestedFormat(Json.format[GradientBoostRegressionTree])
    )
  )

  implicit object RegressionIdentity extends BSONObjectIdentity[Regression] {
    def of(entity: Regression): Option[BSONObjectID] = entity._id

    protected def set(entity: Regression, id: Option[BSONObjectID]) =
      entity match {
        case x: LinearRegression => x.copy(_id = id)
        case x: GeneralizedLinearRegression => x.copy(_id = id)
        case x: RegressionTree => x.copy(_id = id)
        case x: RandomRegressionForest => x.copy(_id = id)
        case x: GradientBoostRegressionTree => x.copy(_id = id)
      }
  }
}