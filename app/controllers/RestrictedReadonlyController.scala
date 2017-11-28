package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import controllers.core.{CrudController, ReadonlyController}
import models.FilterCondition
import play.api.mvc.{Action, AnyContent, BodyParser}
import util.SecurityUtil
import util.SecurityUtil._
import play.api.mvc.BodyParsers.parse

trait RestrictedReadonlyController[ID] extends ReadonlyController[ID] {

  abstract override def get(id: ID): Action[AnyContent] =
    restrictAny(super.get(id))

  abstract override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] =
    restrictAny(super.find(page, orderBy, filter))

  abstract override def listAll(orderBy: String): Action[AnyContent] =
    restrictAny(super.listAll(orderBy))

  protected def restrict[A](
    bodyParser: BodyParser[A])(
    action: AuthenticatedAction[A]
  ): Action[A]

  protected def restrictAny(
    action: Action[AnyContent]
  ): Action[AnyContent] =
    restrict[AnyContent](parse.anyContent)(SecurityUtil.toAuthenticatedAction(action))

  protected def restrictAny(
    action: AuthenticatedAction[AnyContent]
  ): Action[AnyContent] =
    restrict[AnyContent](parse.anyContent)(action)
}

trait RestrictedCrudController[ID] extends RestrictedReadonlyController[ID] with CrudController[ID] {

  abstract override def create: Action[AnyContent] =
    restrictAny(super.create)

  abstract override def edit(id: ID): Action[AnyContent] =
    restrictAny(super.edit(id))

  abstract override def save: Action[AnyContent] =
    restrictAny(super.save)

  abstract override def update(id: ID): Action[AnyContent] =
    restrictAny(super.update(id))

  abstract override def delete(id: ID): Action[AnyContent] =
    restrictAny(super.delete(id))
}

// Admin restricted

trait AdminRestricted {

  def deadbolt: DeadboltActions

  protected def restrict[A](
    bodyParser: BodyParser[A])(
    action: AuthenticatedAction[A]
  ) = restrictAdmin[A](deadbolt, bodyParser)(action)
}

trait AdminRestrictedReadonlyController[ID] extends RestrictedReadonlyController[ID] with AdminRestricted

trait AdminRestrictedCrudController[ID] extends RestrictedCrudController[ID] with AdminRestricted


// Subject present restricted

trait SubjectPresentRestricted {

  def deadbolt: DeadboltActions

  protected def restrict[A](
    bodyParser: BodyParser[A])(
    action: AuthenticatedAction[A]
  ) = restrictSubjectPresent[A](deadbolt, bodyParser)(action)
}

trait SubjectPresentRestrictedReadonlyController[ID] extends RestrictedReadonlyController[ID] with SubjectPresentRestricted

trait SubjectPresentRestrictedCrudController[ID] extends RestrictedCrudController[ID] with SubjectPresentRestricted