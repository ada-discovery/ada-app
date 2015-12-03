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

class DeNoPaFirstVisitController @Inject() (
    @Named("DeNoPaFirstVisitRepo") repo: JsObjectCrudRepo,
    tranSMARTService: TranSMARTService,
    deNoPaTypeStats : DeNoPaTypeStats
  ) extends DeNoPaController(repo, tranSMARTService) {

  private lazy val typeStats = deNoPaTypeStats.collectFirstVisitGlobalTypeStats

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "b_Gruppe"))

  override protected val csvFileName = "denopa-firstvisit"

  override protected val transSMARTDataFileName = "denopa-firstvisit_data_file"

  override protected val transSMARTMappingFileName = "denopa-firstvisit_mapping_file"

  override protected def showView(id : BSONObjectID, item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.jsonShow(
      "First Visit Item",
      item,
      routes.DeNoPaFirstVisitController.find()
    )

  override protected def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.list(
      "first visit record",
      currentPage,
      currentOrderBy,
      currentFilter,
      listViewColumns.get,
      routes.DeNoPaFirstVisitController.find,
      routes.DeNoPaFirstVisitController.find(),
      routes.DeNoPaFirstVisitController.get,
      routes.DeNoPaFirstVisitController.exportRecordsAsCsv(),
      routes.DeNoPaFirstVisitController.exportTransSMARTDataFile(),
      routes.DeNoPaFirstVisitController.exportTransSMARTMappingFile()
    )

  def overview = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    Ok(views.html.denopa.typeOverview("First Visit Type Overview", typeStats))
  }
}