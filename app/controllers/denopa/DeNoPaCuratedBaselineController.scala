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

class DeNoPaCuratedBaselineController @Inject() (
    @Named("DeNoPaCuratedBaselineRepo") repo: JsObjectCrudRepo,
    tranSMARTService: TranSMARTService
  ) extends DeNoPaController(repo, tranSMARTService) {

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"))

  override protected val csvFileName = "denopa-curated-baseline"

  override protected val transSMARTDataFileName = "denopa-curated-baseline_data_file"

  override protected val transSMARTMappingFileName = "denopa-curated-baseline_mapping_file"

  override protected def showView(id : BSONObjectID, item : JsObject)(implicit msg: Messages, request: RequestHeader) =
    html.jsonShow(
      "Curated baseline Item",
      item,
      routes.DeNoPaCuratedBaselineController.find()
    )

  override protected def listView(currentPage: Page[JsObject], currentOrderBy: String, currentFilter: String)(implicit msg: Messages, request: RequestHeader) =
    html.denopa.list(
      "curated baseline record",
      currentPage,
      currentOrderBy,
      currentFilter,
      listViewColumns.get,
      routes.DeNoPaCuratedBaselineController.find,
      routes.DeNoPaCuratedBaselineController.find(),
      routes.DeNoPaCuratedBaselineController.get,
      routes.DeNoPaCuratedBaselineController.exportRecordsAsCsv(),
      routes.DeNoPaCuratedBaselineController.exportTransSMARTDataFile(),
      routes.DeNoPaCuratedBaselineController.exportTransSMARTMappingFile()
    )
}