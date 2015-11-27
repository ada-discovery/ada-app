package controllers

import javax.inject.{Inject, Named}

import play.api.mvc.Action
import models.Page
import persistence.{JsObjectCrudRepo}
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{Json, JsObject}
import play.api.mvc.RequestHeader
import services.TranSMARTService
import standalone.DeNoPaTypeStats
import views.html

class DeNoPaBaselineController @Inject() (
    @Named("DeNoPaBaselineRepo") repo: JsObjectCrudRepo,
    tranSMARTService: TranSMARTService,
    deNoPaTypeStats : DeNoPaTypeStats,
    messagesApi: MessagesApi
  ) extends DeNoPaController(repo, tranSMARTService, messagesApi) {

  lazy val typeStats = deNoPaTypeStats.collectBaselineGlobalTypeStats

  override val listViewColumns = List("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe")

  override val csvFileName = "denopa-baseline"

  override val transSMARTDataFileName = "denopa-baseline_data_file"

  override val transSMARTMappingFileName = "denopa-baseline_mapping_file"

  override def showView(item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.jsonShow(
      "Baseline Item",
      item,
      routes.DeNoPaBaselineController.find()
    )

  override def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.list(
      "baseline record",
      currentPage,
      currentOrderBy,
      currentFilter,
      listViewColumns,
      routes.DeNoPaBaselineController.find,
      routes.DeNoPaBaselineController.find(),
      routes.DeNoPaBaselineController.get,
      routes.DeNoPaBaselineController.exportRecordsAsCsv(),
      routes.DeNoPaBaselineController.exportTransSMARTDataFile(),
      routes.DeNoPaBaselineController.exportTransSMARTMappingFile()
    )

  def overview = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(html.denopa.typeOverview("Baseline Type Overview", typeStats))
  }
}