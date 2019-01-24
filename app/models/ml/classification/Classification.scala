package models.ml.classification

import dataaccess.BSONObjectIdentity
import models.json._
import play.api.libs.json.{Format, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._

import org.incal.spark_ml.models.classification._
import org.incal.spark_ml.models.TreeCore

object Classification {

  implicit val logisticModelFamilyEnumTypeFormat = EnumFormat.enumFormat(LogisticModelFamily)
  implicit val mlpSolverEnumTypeFormat = EnumFormat.enumFormat(MLPSolver)
  implicit val featureSubsetStrategyEnumTypeFormat = EnumFormat.enumFormat(RandomForestFeatureSubsetStrategy)
  implicit val decisionTreeImpurityEnumTypeFormat = EnumFormat.enumFormat(DecisionTreeImpurity)
  implicit val gbtClassificationLossTypeEnumTypeFormat = EnumFormat.enumFormat(GBTClassificationLossType)
  implicit val bayesModelTypeEnumTypeFormat = EnumFormat.enumFormat(BayesModelType)

  def eitherFormat[T: Format] = {
    implicit val optionFormat = new OptionFormat[T]
    EitherFormat[Option[T], Seq[T]]
  }

  implicit val doubleEitherFormat = eitherFormat[Double]
  implicit val intEitherFormat = eitherFormat[Int]

  private implicit val treeCoreFormat = Json.format[TreeCore]

  implicit val classificationFormat: Format[ClassificationModel] = new SubTypeFormat[ClassificationModel](
    Seq(
      ManifestedFormat(Json.format[LogisticRegression]),
      ManifestedFormat(Json.format[MultiLayerPerceptron]),
      ManifestedFormat(Json.format[DecisionTree]),
      ManifestedFormat(Json.format[RandomForest]),
      ManifestedFormat(Json.format[GradientBoostTree]),
      ManifestedFormat(Json.format[NaiveBayes]),
      ManifestedFormat(Json.format[LinearSupportVectorMachine])
    )
  )

  implicit object ClassificationIdentity extends BSONObjectIdentity[ClassificationModel] {
    def of(entity: ClassificationModel): Option[BSONObjectID] = entity._id

    protected def set(entity: ClassificationModel, id: Option[BSONObjectID]) =
      entity match {
        case x: LogisticRegression => x.copy(_id = id)
        case x: MultiLayerPerceptron => x.copy(_id = id)
        case x: DecisionTree => x.copy(_id = id)
        case x: RandomForest => x.copy(_id = id)
        case x: GradientBoostTree => x.copy(_id = id)
        case x: NaiveBayes => x.copy(_id = id)
        case x: LinearSupportVectorMachine => x.copy(_id = id)
      }
  }
}