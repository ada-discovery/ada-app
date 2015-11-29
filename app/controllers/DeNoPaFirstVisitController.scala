package controllers

import javax.inject.{Named, Inject}

import play.api.mvc.Action
import models.Page
import persistence.RepoTypeRegistry._
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{Json, JsObject}
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import services.TranSMARTService
import standalone.DeNoPaTypeStats
import views.html

class DeNoPaFirstVisitController @Inject() (
    @Named("DeNoPaFirstVisitRepo") repo: JsObjectCrudRepo,
    tranSMARTService: TranSMARTService,
    deNoPaTypeStats : DeNoPaTypeStats,
    messagesApi: MessagesApi
  ) extends DeNoPaController(repo, tranSMARTService, messagesApi) {

  lazy val typeStats = deNoPaTypeStats.collectFirstVisitGlobalTypeStats

  override val listViewColumns = List("Line_Nr", "Probanden_Nr", "Geb_Datum", "b_Gruppe")

  override val csvFileName = "denopa-firstvisit"

  override val transSMARTDataFileName = "denopa-firstvisit_data_file"

  override val transSMARTMappingFileName = "denopa-firstvisit_mapping_file"

  override def showView(item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.jsonShow(
      "First Visit Item",
      item,
      routes.DeNoPaFirstVisitController.find()
    )

  override def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.list(
      "first visit record",
      currentPage,
      currentOrderBy,
      currentFilter,
      listViewColumns,
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