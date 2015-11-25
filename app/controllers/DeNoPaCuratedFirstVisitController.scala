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

  override val listViewColumns = List("Line_Nr", "Probanden_Nr", "Geb_Datum", "b_Gruppe")

  override val csvFileName = "denopa-curated-firstvisit"

  override val transSMARTDataFileName = "denopa-curated-firstvisit_data_file"

  override val transSMARTMappingFileName = "denopa-curated-firstvisit_mapping_file"

  override def showView(item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.jsonShow(
      "Curated First Visit Item",
      item,
      routes.DeNoPaCuratedFirstVisitController.find()
    )

  override def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.list(
      "curated first visit record",
      currentPage,
      currentOrderBy,
      currentFilter,
      listViewColumns,
      routes.DeNoPaCuratedFirstVisitController.find,
      routes.DeNoPaCuratedFirstVisitController.find(),
      routes.DeNoPaCuratedFirstVisitController.get,
      routes.DeNoPaCuratedFirstVisitController.exportRecordsAsCsv(),
      routes.DeNoPaCuratedFirstVisitController.exportTransSMARTDataFile(),
      routes.DeNoPaCuratedFirstVisitController.exportTransSMARTMappingFile()
    )
}