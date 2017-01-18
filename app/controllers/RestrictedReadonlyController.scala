package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import models.FilterCondition
import play.api.mvc.{Action, AnyContent}
import util.SecurityUtil._

trait RestrictedReadonlyController[ID] extends ReadonlyController[ID] {

  abstract override def get(id: ID): Action[AnyContent] =
    restrict(super.get(id))

  abstract override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]): Action[AnyContent] =
    restrict(super.find(page, orderBy, filter))

  abstract override def listAll(orderBy: String): Action[AnyContent] =
    restrict(super.listAll(orderBy))

  protected def restrict[A](action: Action[A]): Action[A]
}

trait RestrictedCrudController[ID] extends RestrictedReadonlyController[ID] with CrudController[ID] {

  abstract override def create: Action[AnyContent] =
    restrict(super.create)

  abstract override def edit(id: ID): Action[AnyContent] =
    restrict(super.edit(id))

  abstract override def save: Action[AnyContent] =
    restrict(super.save)

  abstract override def update(id: ID): Action[AnyContent] =
    restrict(super.update(id))

  abstract override def delete(id: ID): Action[AnyContent] =
    restrict(super.delete(id))
}

// Admin restricted

trait AdminRestricted {

  def deadbolt: DeadboltActions

  protected def restrict[A](action: Action[A]) = restrictAdmin[A](deadbolt)(action)
}

trait AdminRestrictedReadonlyController[ID] extends RestrictedReadonlyController[ID] with AdminRestricted

trait AdminRestrictedCrudController[ID] extends RestrictedCrudController[ID] with AdminRestricted


// Subject present restricted

trait SubjectPresentRestricted {

  def deadbolt: DeadboltActions

  protected def restrict[A](action: Action[A]) = restrictSubjectPresent[A](deadbolt)(action)
}

trait SubjectPresentRestrictedReadonlyController[ID] extends RestrictedReadonlyController[ID] with SubjectPresentRestricted

trait SubjectPresentRestrictedCrudController[ID] extends RestrictedCrudController[ID] with SubjectPresentRestricted