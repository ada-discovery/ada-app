package controllers.dataset

import controllers.CrudController
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait CategoryController extends CrudController[BSONObjectID] {

  def getCategoryD3Root: Action[AnyContent]

  def relocateToParent(id: BSONObjectID, parentId: Option[BSONObjectID]): Action[AnyContent]

  def jsRoutes: Action[AnyContent]
}