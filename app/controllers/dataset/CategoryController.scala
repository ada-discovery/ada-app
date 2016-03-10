package controllers.dataset

import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import util.FilterSpec

trait CategoryController {

  def dataSetId: String

  def find(page: Int, orderBy: String, filter: FilterSpec): Action[AnyContent]

  def get(id: BSONObjectID): Action[AnyContent]

  def create: Action[AnyContent]

  def edit(id: BSONObjectID): Action[AnyContent]

  def save: Action[AnyContent]

  def update(id: BSONObjectID): Action[AnyContent]

  def delete(id: BSONObjectID): Action[AnyContent]

  def getCategoryD3Root: Action[AnyContent]
}