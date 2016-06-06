package controllers

import play.api.mvc.{Action, AnyContent}

trait AdminRestrictedCrudController[ID] extends AdminRestrictedReadonlyController[ID] with CrudController[ID] {

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