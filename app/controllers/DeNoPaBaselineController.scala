package controllers

import javax.inject.Inject

import play.api.mvc.Action
import models.Page
import persistence.DeNoPaBaselineRepo
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{Json, JsObject}
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import views.html

class DeNoPaBaselineController @Inject() (
    repo: DeNoPaBaselineRepo,
    messagesApi: MessagesApi
  ) extends DeNoPaController(repo, messagesApi) {

  override def listViewProjection = Json.obj("Line_Nr" -> 1, "Probanden_Nr" -> 1, "Geb_Datum" -> 1, "a_Gruppe" -> 1, "b_Gruppe" -> 1)

  override def showView(item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.showBaseline(item).asInstanceOf[Html]

  override def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.listBaseline(currentPage, currentOrderBy, currentFilter).asInstanceOf[Html]

  def overview = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(views.html.denopa.overviewBaseline("LALA"))
  }
}