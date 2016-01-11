package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.DataSetRouter
import persistence.DictionaryFieldRepo

class DeNoPaCuratedFirstVisitController @Inject() (
    @Named("DeNoPaCuratedFirstVisitDictionaryRepo") repo: DictionaryFieldRepo
  ) extends DeNoPaController(repo) {

  override protected val dataSetName = "DeNoPa Curated First Visit"

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "b_Gruppe"))

  override protected val overviewFiledNamesConfPrefix = "denopa.curatedfirstvisit"

  override protected def router = DataSetRouter(
    routes.DeNoPaCuratedFirstVisitController.find,
    routes.DeNoPaCuratedFirstVisitController.find(),
    routes.DeNoPaCuratedFirstVisitController.get,
    routes.DeNoPaCuratedFirstVisitController.exportRecordsAsCsv(),
    routes.DeNoPaCuratedFirstVisitController.exportRecordsAsJson(),
    routes.DeNoPaCuratedFirstVisitController.exportTranSMARTDataFile(),
    routes.DeNoPaCuratedFirstVisitController.exportTranSMARTMappingFile(),
    routes.DeNoPaCuratedFirstVisitController.getScatterStats()
  )
}