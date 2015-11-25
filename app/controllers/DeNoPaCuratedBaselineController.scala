package controllers

import javax.inject.Inject

import models.Page
import persistence.DeNoPaCuratedBaselineRepo
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, RequestHeader}
import services.TranSMARTService
import views.html

class DeNoPaCuratedBaselineController @Inject() (
    repo: DeNoPaCuratedBaselineRepo,
    tranSMARTService: TranSMARTService,
    messagesApi: MessagesApi
  ) extends DeNoPaController(repo, tranSMARTService, messagesApi) {

  override val listViewColumns = List("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe")

  override val csvFileName = "denopa-curated-baseline"

  override val transSMARTDataFileName = "denopa-curated-baseline_data_file"

  override val transSMARTMappingFileName = "denopa-curated-baseline_mapping_file"

  override def showView(item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.jsonShow(
      "Curated baseline Item",
      item,
      routes.DeNoPaCuratedBaselineController.find()
    )

  override def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.list(
      "curated baseline record",
      currentPage,
      currentOrderBy,
      currentFilter,
      listViewColumns,
      routes.DeNoPaCuratedBaselineController.find,
      routes.DeNoPaCuratedBaselineController.find(),
      routes.DeNoPaCuratedBaselineController.get,
      routes.DeNoPaCuratedBaselineController.exportRecordsAsCsv(),
      routes.DeNoPaCuratedBaselineController.exportTransSMARTDataFile(),
      routes.DeNoPaCuratedBaselineController.exportTransSMARTMappingFile()
    )
}