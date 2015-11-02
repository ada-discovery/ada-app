package controllers

import javax.inject.Inject

import play.api.mvc.Action
import models.Page
import persistence.DeNoPaFirstVisitRepo
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{Json, JsObject}
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import standalone.DeNoPaTypeStats
import views.html

class DeNoPaFirstVisitController @Inject() (
    repo: DeNoPaFirstVisitRepo,
    deNoPaTypeStats : DeNoPaTypeStats,
    messagesApi: MessagesApi
  ) extends DeNoPaController(repo, messagesApi) {

  lazy val typeStats = deNoPaTypeStats.collectFirstVisitGlobalTypeStats

  override def listViewProjection = Json.obj("Line_Nr" -> 1, "Probanden_Nr" -> 1, "Geb_Datum" -> 1, "b_Gruppe" -> 1) // no a_Gruppe here

  override def showView(item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.showFirstVisit(item).asInstanceOf[Html]

  override def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.listFirstVisit(currentPage, currentOrderBy, currentFilter).asInstanceOf[Html]

  def overview = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(views.html.denopa.typeOverview("First Visit Type Overview", typeStats))
  }
}