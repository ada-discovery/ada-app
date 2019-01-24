package models.ml.regression

import dataaccess.BSONObjectIdentity
import models.json._
import play.api.libs.json.{Format, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import org.incal.spark_ml.models.regression._
import org.incal.spark_ml.models.TreeCore

object Regression {

  implicit val regressionSolverEnumTypeFormat = EnumFormat.enumFormat(RegressionSolver)
  implicit val generalizedLinearRegressionFamilyEnumTypeFormat = EnumFormat.enumFormat(GeneralizedLinearRegressionFamily)
  implicit val generalizedLinearRegressionLinkTypeEnumTypeFormat = EnumFormat.enumFormat(GeneralizedLinearRegressionLinkType)
  implicit val generalizedLinearRegressionSolverEnumTypeFormat = EnumFormat.enumFormat(GeneralizedLinearRegressionSolver)
  implicit val featureSubsetStrategyEnumTypeFormat = EnumFormat.enumFormat(RandomRegressionForestFeatureSubsetStrategy)
  implicit val regressionTreeImpurityEnumTypeFormat = EnumFormat.enumFormat(RegressionTreeImpurity)
  implicit val gbtRegressionLossTypeEnumTypeFormat = EnumFormat.enumFormat(GBTRegressionLossType)

  def eitherFormat[T: Format] = {
    implicit val optionFormat = new OptionFormat[T]
    EitherFormat[Option[T], Seq[T]]
  }

  implicit val doubleEitherFormat = eitherFormat[Double]
  implicit val intEitherFormat = eitherFormat[Int]

  private implicit val treeCoreFormat = Json.format[TreeCore]

  implicit val regressionFormat: Format[RegressionModel] = new SubTypeFormat[RegressionModel](
    Seq(
      ManifestedFormat(Json.format[LinearRegression]),
      ManifestedFormat(Json.format[GeneralizedLinearRegression]),
      ManifestedFormat(Json.format[RegressionTree]),
      ManifestedFormat(Json.format[RandomRegressionForest]),
      ManifestedFormat(Json.format[GradientBoostRegressionTree])
    )
  )

  implicit object RegressionIdentity extends BSONObjectIdentity[RegressionModel] {
    def of(entity: RegressionModel): Option[BSONObjectID] = entity._id

    protected def set(entity: RegressionModel, id: Option[BSONObjectID]) =
      entity match {
        case x: LinearRegression => x.copy(_id = id)
        case x: GeneralizedLinearRegression => x.copy(_id = id)
        case x: RegressionTree => x.copy(_id = id)
        case x: RandomRegressionForest => x.copy(_id = id)
        case x: GradientBoostRegressionTree => x.copy(_id = id)
      }
  }
}