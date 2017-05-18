package controllers.core

import play.api.data.Form
import play.api.mvc.Request
import play.twirl.api.Html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait HasEditView[E, ID] {

  protected type EditViewData

  protected type EditView = WebContext => EditViewData => Html

  protected def getEditViewData(id: ID, item: E): Request[_] => Future[EditViewData]

  protected[controllers] def editView: EditView

  protected def editViewWithContext(
    data: EditViewData)(
    implicit context: WebContext
  ) = editView(context)(data)
}

trait HasFormEditView[E, ID] extends HasEditView[E, ID] {

  protected def getFormEditViewData(id: ID, form: Form[E]): Request[_] => Future[EditViewData]

  override protected def getEditViewData(id: ID, item: E) = getFormEditViewData(id, fillForm(item))

  protected def fillForm(item: E): Form[E]
}

trait HasBasicFormEditView[E, ID] extends HasFormEditView[E, ID] {

  override protected type EditViewData = IdForm[ID, E]

  override protected def getFormEditViewData(id: ID, form: Form[E]) = { _ => Future(IdForm(id, form)) }
}