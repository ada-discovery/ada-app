package controllers.core

import play.api.data.Form
import play.twirl.api.Html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait HasCreateView {

  protected type CreateViewData

  protected def getCreateViewData: Future[CreateViewData]

  protected def createView: WebContext => CreateViewData => Html

  protected def createViewWithContext(
    data: CreateViewData)(
    implicit context: WebContext
  ) = createView(context)(data)
}

trait HasFormCreateView[E] extends HasCreateView {

  protected def getFormCreateViewData(form: Form[E]): Future[CreateViewData]

  override protected def getCreateViewData = getFormCreateViewData(form)

  protected def form: Form[E]
}

trait HasBasicFormCreateView[E] extends HasFormCreateView[E] {

  override protected type CreateViewData = Form[E]

  override protected def getFormCreateViewData(form: Form[E]) = Future(form)
}
