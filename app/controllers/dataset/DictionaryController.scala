package controllers.dataset

import play.api.mvc.{Action, AnyContent}
import util.FilterSpec

trait DictionaryController {

  def find(page: Int, orderBy: String, filter: FilterSpec): Action[AnyContent]

  def listAll(orderBy: Int): Action[AnyContent]

  def get(id: String): Action[AnyContent]

  def create: Action[AnyContent]

  def edit(id: String): Action[AnyContent]

  def save: Action[AnyContent]

  def update(id: String): Action[AnyContent]

  def delete(id: String): Action[AnyContent]

  def overviewList(page: Int, orderBy: String, filter: FilterSpec): Action[AnyContent]

  def inferDictionary: Action[AnyContent]
}