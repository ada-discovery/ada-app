package controllers.dataset

import controllers.CrudController
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait DataViewController extends CrudController[BSONObjectID] {}