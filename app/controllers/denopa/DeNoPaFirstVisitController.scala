package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{DictionaryRouter, DataSetRouter}
import persistence.DictionaryFieldRepo

class DeNoPaFirstVisitController @Inject() (
    @Named("DeNoPaFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DeNoPaController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa First Visit"

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "b_Gruppe"))

  override protected val overviewFieldNamesConfPrefix = "denopa.firstvisit"

  override protected def router = DataSetRouter(
    routes.DeNoPaFirstVisitController.find,
    routes.DeNoPaFirstVisitController.find(),
    routes.DeNoPaFirstVisitController.overviewList,
    routes.DeNoPaFirstVisitController.overviewList(),
    routes.DeNoPaFirstVisitController.get,
    routes.DeNoPaFirstVisitController.exportAllRecordsAsCsv(),
    routes.DeNoPaFirstVisitController.exportAllRecordsAsJson(),
    routes.DeNoPaFirstVisitController.exportRecordsAsCsv(),
    routes.DeNoPaFirstVisitController.exportRecordsAsJson(),
    routes.DeNoPaFirstVisitController.exportTranSMARTDataFile(),
    routes.DeNoPaFirstVisitController.exportTranSMARTMappingFile(),
    routes.DeNoPaFirstVisitController.getScatterStats()
  )

  override protected def dictionaryRouter = DictionaryRouter(
    routes.DeNoPaFirstVisitController.dictionary,
    routes.DeNoPaFirstVisitController.dictionary(),
    routes.DeNoPaFirstVisitController.getField
  )
}