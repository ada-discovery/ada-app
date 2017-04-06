package controllers.core

import play.api.data.Form
import play.twirl.api.Html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait HasEditView[E, ID] {

  protected type EditViewData

  protected def getEditViewData(id: ID, item: E): Future[EditViewData]

  protected def editView: WebContext => EditViewData => Html

  protected def editViewWithContext(
    data: EditViewData)(
    implicit context: WebContext
  ) = editView(context)(data)
}

trait HasFormEditView[E, ID] extends HasEditView[E, ID] {

  protected def getFormEditViewData(id: ID, form: Form[E]): Future[EditViewData]

  override protected def getEditViewData(id: ID, item: E) = getFormEditViewData(id, form.fill(item))

  protected def form: Form[E]
}

trait HasBasicFormEditView[E, ID] extends HasFormEditView[E, ID] {

  override protected type EditViewData = IdForm[ID, E]

  override protected def getFormEditViewData(id: ID, form: Form[E]) = Future(IdForm(id, form))
}