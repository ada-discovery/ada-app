package controllers

import javax.inject.Inject

import models.Page
import persistence.DeNoPaCuratedBaselineRepo
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, RequestHeader}
import views.html

class DeNoPaCuratedBaselineController @Inject() (
    repo: DeNoPaCuratedBaselineRepo,
    messagesApi: MessagesApi
  ) extends DeNoPaController(repo, messagesApi) {

  override def listViewProjection = Json.obj("Line_Nr" -> 1, "Probanden_Nr" -> 1, "Geb_Datum" -> 1, "a_Gruppe" -> 1, "b_Gruppe" -> 1)

  override def showView(item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.showCuratedBaseline(item)

  override def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.listCuratedBaseline(currentPage, currentOrderBy, currentFilter)

  def exportRecordsAsCsv(delimiter : String) = exportRecordsAsCsvTo("denopa-curated-baseline", delimiter)
}