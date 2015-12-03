package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.routes
import models.Page
import persistence.RepoTypeRegistry._
import play.api.i18n.Messages
import play.api.libs.json.JsObject
import play.api.mvc.RequestHeader
import reactivemongo.bson.BSONObjectID
import services.TranSMARTService
import views.html

class DeNoPaCuratedFirstVisitController @Inject() (
    @Named("DeNoPaCuratedFirstVisitRepo") repo: JsObjectCrudRepo,
    tranSMARTService: TranSMARTService
  ) extends DeNoPaController(repo, tranSMARTService) {

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "b_Gruppe"))

  override protected val csvFileName = "denopa-curated-firstvisit"

  override protected val transSMARTDataFileName = "denopa-curated-firstvisit_data_file"

  override protected val transSMARTMappingFileName = "denopa-curated-firstvisit_mapping_file"

  override protected def showView(id : BSONObjectID, item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.jsonShow(
      "Curated First Visit Item",
      item,
      routes.DeNoPaCuratedFirstVisitController.find()
    )

  override protected def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.list(
      "curated first visit record",
      currentPage,
      currentOrderBy,
      currentFilter,
      listViewColumns.get,
      routes.DeNoPaCuratedFirstVisitController.find,
      routes.DeNoPaCuratedFirstVisitController.find(),
      routes.DeNoPaCuratedFirstVisitController.get,
      routes.DeNoPaCuratedFirstVisitController.exportRecordsAsCsv(),
      routes.DeNoPaCuratedFirstVisitController.exportTransSMARTDataFile(),
      routes.DeNoPaCuratedFirstVisitController.exportTransSMARTMappingFile()
    )
}