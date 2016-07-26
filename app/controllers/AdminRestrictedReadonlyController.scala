package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import play.api.mvc.{AnyContent, Action}
import util.FilterCondition
import util.SecurityUtil._

trait AdminRestrictedReadonlyController[ID] extends ReadonlyController[ID] {

  def deadbolt: DeadboltActions

  abstract override def get(id: ID): Action[AnyContent] =
    restrict(super.get(id))

  abstract override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] =
    restrict(super.find(page, orderBy, filter))

  abstract override def listAll(orderBy: String): Action[AnyContent] =
    restrict(super.listAll(orderBy))

  protected def restrict[A] = restrictAdmin[A](deadbolt)_
}
