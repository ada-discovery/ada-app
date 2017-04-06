package controllers.core

import play.api.data.Form
import play.twirl.api.Html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait HasShowView[E, ID] {

  protected type ShowViewData

  protected def getShowViewData(id: ID, item: E): Future[ShowViewData]

  protected def showView: WebContext => ShowViewData => Html

  protected def showViewWithContext(
    data: ShowViewData)(
    implicit context: WebContext
  ) = showView(context)(data)
}

trait HasFormShowView[E, ID] extends HasShowView[E, ID] {

  protected def getFormShowViewData(id: ID, form: Form[E]): Future[ShowViewData]

  override protected def getShowViewData(id: ID, item: E) = getFormShowViewData(id, form.fill(item))

  protected def form: Form[E]
}

trait HasBasicFormShowView[E, ID] extends HasFormShowView[E, ID] {

  override protected type ShowViewData = IdForm[ID, E]

  override protected def getFormShowViewData(id: ID, form: Form[E]) = Future(IdForm(id, form))
}

trait HasShowEqualEditView[E, ID] extends HasShowView[E, ID] {
  self: HasEditView[E, ID] =>

  override protected type ShowViewData = EditViewData

  override protected def showView = editView
}

trait HasFormShowEqualEditView[E, ID] extends HasFormShowView[E, ID] {
  self: HasFormEditView[E, ID] =>

  override protected type ShowViewData = EditViewData

  override protected def getFormShowViewData(id: ID, form: Form[E]) = getFormEditViewData(id, form)

  override protected def showView = editView
}
