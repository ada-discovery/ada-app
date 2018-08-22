package controllers.dataset

import models.Filter
import org.incal.play.controllers.CrudController
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait FilterController extends CrudController[BSONObjectID] {
  def saveAjax(filter: Filter): Action[AnyContent]
  def idAndNames: Action[AnyContent]
  def idAndNamesAccessible: Action[AnyContent]
}