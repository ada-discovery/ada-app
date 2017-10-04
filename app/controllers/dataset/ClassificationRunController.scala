package controllers.dataset

import controllers.core.ReadonlyController
import models.ml.ClassificationSetting
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait ClassificationRunController extends ReadonlyController[BSONObjectID]{

  def create: Action[AnyContent]

  def classify(
    setting: ClassificationSetting,
    saveResults: Boolean
  ): Action[AnyContent]

  def selectFeaturesAsChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int,
    discretizerBucketsNum: Int
  ): Action[AnyContent]
}