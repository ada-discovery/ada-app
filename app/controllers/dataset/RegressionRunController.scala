package controllers.dataset

import controllers.core.ReadonlyController
import models.ml.RegressionSetting
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait RegressionRunController extends ReadonlyController[BSONObjectID]{

  def create: Action[AnyContent]

  def regress(
    setting: RegressionSetting,
    saveResults: Boolean
  ): Action[AnyContent]

  def delete(id: BSONObjectID): Action[AnyContent]
}