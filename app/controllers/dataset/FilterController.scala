package controllers.dataset

import controllers.core.CrudController
import models.Filter
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait FilterController extends CrudController[BSONObjectID] {
  def saveAjax(filter: Filter): Action[AnyContent]
  def getIdAndNames: Action[AnyContent]
}