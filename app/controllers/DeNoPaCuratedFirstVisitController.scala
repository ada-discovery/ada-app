package controllers

import javax.inject.Inject

import models.Page
import persistence.DeNoPaCuratedFirstVisitRepo
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, RequestHeader}
import play.twirl.api.Html
import services.TranSMARTService
import views.html

class DeNoPaCuratedFirstVisitController @Inject() (
    repo: DeNoPaCuratedFirstVisitRepo,
    tranSMARTService: TranSMARTService,
    messagesApi: MessagesApi
  ) extends DeNoPaController(repo, tranSMARTService, messagesApi) {

  override def listViewProjection = Json.obj("Line_Nr" -> 1, "Probanden_Nr" -> 1, "Geb_Datum" -> 1, "b_Gruppe" -> 1) // no a_Gruppe here

  override def showView(item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.showCuratedFirstVisit(item)

  override def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.listCuratedFirstVisit(currentPage, currentOrderBy, currentFilter)

  def exportRecordsAsCsv(delimiter : String) = exportRecordsAsCsvTo("denopa-curated-firstvisit", delimiter)

  def exportTransSMARTDataFile(delimiter : String) = exportTransSMARTDataFileAsCsvTo("denopa-curated-firstvisit_data_file", delimiter)

  def exportTransSMARTMappingFile(delimiter : String) = exportTransSMARTDataFileAsCsvTo("denopa-curated-firstvisit_data_file", "denopa-curated-firstvisit_mapping_file", delimiter)
}