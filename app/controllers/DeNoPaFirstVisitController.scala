package controllers

import javax.inject.Inject

import models.Page
import persistence.DeNoPaFirstVisitRepo
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.JsObject
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import views.html

class DeNoPaFirstVisitController @Inject() (
    repo: DeNoPaFirstVisitRepo,
    messagesApi: MessagesApi
  ) extends DeNoPaController(repo, messagesApi) {

  override def showView(item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.showFirstVisit(item).asInstanceOf[Html]

  override def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.listFirstVisit(currentPage, currentOrderBy, currentFilter).asInstanceOf[Html]
}