package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import models.FilterCondition
import play.api.mvc.{AnyContent, Action}
import util.SecurityUtil._

trait RestrictedReadonlyController[ID] extends ReadonlyController[ID] {

  def deadbolt: DeadboltActions

  abstract override def get(id: ID): Action[AnyContent] =
    restrict(super.get(id))

  abstract override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] =
    restrict(super.find(page, orderBy, filter))

  abstract override def listAll(orderBy: String): Action[AnyContent] =
    restrict(super.listAll(orderBy))

  protected def restrict[A] = restrictAdmin[A](deadbolt)_
}
