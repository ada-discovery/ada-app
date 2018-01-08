package controllers.dataset

import controllers.core.ReadonlyController
import models.ml.{ClassificationEvalMetric, ClassificationSetting}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait ClassificationRunController extends ReadonlyController[BSONObjectID]{

  def create: Action[AnyContent]

  def classify(
    setting: ClassificationSetting,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ): Action[AnyContent]

  def selectFeaturesAsChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int,
    discretizerBucketsNum: Int
  ): Action[AnyContent]

  def selectFeaturesAsAnovaChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int
  ): Action[AnyContent]

  def delete(id: BSONObjectID): Action[AnyContent]
}