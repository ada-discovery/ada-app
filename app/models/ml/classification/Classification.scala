package models.ml.classification

import java.util.Date

import dataaccess.{BSONObjectIdentity, EnumFormat, ManifestedFormat, SubTypeFormat}
import models.ml.TreeCore
import play.api.libs.json.{Format, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

abstract class Classification {
  val _id: Option[BSONObjectID]
  val name: Option[String]
  val createdById: Option[BSONObjectID]
  val timeCreated: Date
}

object LogisticModelFamily extends Enumeration {
  val Auto = Value("auto")
  val Binomial = Value("binomial")
  val Multinomial = Value("multinomial")
}

case class LogisticRegression(
  _id: Option[BSONObjectID] = None,
  regularization: Option[Double] = None,
  elasticMixingRatio: Option[Double] = None,
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  fitIntercept: Option[Boolean] = None,
  family: Option[LogisticModelFamily.Value] = None,
  standardization: Option[Boolean] = None,
  aggregationDepth: Option[Int] = None,
  threshold: Option[Double] = None,
  thresholds: Option[Seq[Double]] = None,  // used for multinomial logistic regression
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Classification

object MLPSolver extends Enumeration {
  val LBFGS = Value("l-bfgs")
  val GD = Value("gd")
}

case class MultiLayerPerceptron(
  _id: Option[BSONObjectID] = None,
  layers: Seq[Int],
  maxIteration: Option[Int] = None,
  tolerance: Option[Double] = None,
  blockSize: Option[Int] = None,
  solver: Option[MLPSolver.Value] = None,
  seed: Option[Long] = None,
  stepSize: Option[Double] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Classification

object DecisionTreeImpurity extends Enumeration {
  val entropy, gini = Value
}

object RandomForestFeatureSubsetStrategy extends Enumeration {
  val auto, all, onethird, sqrt, log2 = Value
}

case class DecisionTree(
  _id: Option[BSONObjectID] = None,
  core: TreeCore = TreeCore(),
  impurity: Option[DecisionTreeImpurity.Value] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Classification

case class RandomForest(
  _id: Option[BSONObjectID] = None,
  core: TreeCore = TreeCore(),
  numTrees: Option[Int] = None,
  subsamplingRate: Option[Double] = None,
  impurity: Option[DecisionTreeImpurity.Value] = None,
  featureSubsetStrategy: Option[RandomForestFeatureSubsetStrategy.Value] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Classification

object GBTClassificationLossType extends Enumeration {
  val logistic = Value
}

case class GradientBoostTree(
  _id: Option[BSONObjectID] = None,
  core: TreeCore = TreeCore(),
  maxIteration: Option[Int] = None,
  stepSize: Option[Double] = None,
  subsamplingRate: Option[Double] = None,
  lossType: Option[GBTClassificationLossType.Value] = None,
//  impurity: Option[Impurity.Value] = None
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Classification

object BayesModelType extends Enumeration {
  val multinomial, bernoulli = Value
}

case class NaiveBayes(
  _id: Option[BSONObjectID] = None,
  smoothing: Option[Double] = None,
  modelType: Option[BayesModelType.Value] = None,
  name: Option[String] = None,
  createdById: Option[BSONObjectID] = None,
  timeCreated: Date = new Date()
) extends Classification

object Classification {
  implicit val logisticModelFamilyEnumTypeFormat = EnumFormat.enumFormat(LogisticModelFamily)
  implicit val mlpSolverEnumTypeFormat = EnumFormat.enumFormat(MLPSolver)
  implicit val featureSubsetStrategyEnumTypeFormat = EnumFormat.enumFormat(RandomForestFeatureSubsetStrategy)
  implicit val decisionTreeImpurityEnumTypeFormat = EnumFormat.enumFormat(DecisionTreeImpurity)
  implicit val gbtClassificationLossTypeEnumTypeFormat = EnumFormat.enumFormat(GBTClassificationLossType)
  implicit val bayesModelTypeEnumTypeFormat = EnumFormat.enumFormat(BayesModelType)

  private implicit val treeCoreFormat = Json.format[TreeCore]

  implicit val regressionFormat: Format[Classification] = new SubTypeFormat[Classification](
    Seq(
      ManifestedFormat(Json.format[LogisticRegression]),
      ManifestedFormat(Json.format[MultiLayerPerceptron]),
      ManifestedFormat(Json.format[DecisionTree]),
      ManifestedFormat(Json.format[RandomForest]),
      ManifestedFormat(Json.format[GradientBoostTree]),
      ManifestedFormat(Json.format[NaiveBayes])
    )
  )

  implicit object ClassificationIdentity extends BSONObjectIdentity[Classification] {
    def of(entity: Classification): Option[BSONObjectID] = entity._id

    protected def set(entity: Classification, id: Option[BSONObjectID]) =
      entity match {
        case x: LogisticRegression => x.copy(_id = id)
        case x: MultiLayerPerceptron => x.copy(_id = id)
        case x: DecisionTree => x.copy(_id = id)
        case x: RandomForest => x.copy(_id = id)
        case x: GradientBoostTree => x.copy(_id = id)
        case x: NaiveBayes => x.copy(_id = id)
      }
  }
}