package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.routes
import models.Page
import persistence.RepoTypeRegistry._
import play.api.i18n.Messages
import play.api.libs.json.JsObject
import play.api.mvc.{Action, RequestHeader}
import reactivemongo.bson.BSONObjectID
import services.TranSMARTService
import standalone.DeNoPaTypeStats
import views.html

class DeNoPaBaselineController @Inject() (
    @Named("DeNoPaBaselineRepo") repo: JsObjectCrudRepo,
    tranSMARTService: TranSMARTService,
    deNoPaTypeStats : DeNoPaTypeStats
  ) extends DeNoPaController(repo, tranSMARTService) {

  private lazy val typeStats = deNoPaTypeStats.collectBaselineGlobalTypeStats

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"))

  override protected val csvFileName = "denopa-baseline"

  override protected val transSMARTDataFileName = "denopa-baseline_data_file"

  override protected val transSMARTMappingFileName = "denopa-baseline_mapping_file"

  override protected def showView(id : BSONObjectID, item : JsObject)(implicit msg: Messages, request: RequestHeader) =
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
      listViewColumns.get,
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