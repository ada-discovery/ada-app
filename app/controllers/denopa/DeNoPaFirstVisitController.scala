package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.DataSetRouter
import persistence.DictionaryFieldRepo

class DeNoPaFirstVisitController @Inject() (
    @Named("DeNoPaFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DeNoPaController(dictionaryRepo) {

  override protected def showTitle = "First Visit Item"

  override protected def listTitle = "first visit item"

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "b_Gruppe"))

  override protected val csvFileName = "denopa-firstvisit.csv"

  override protected val jsonFileName = "denopa-firstvisit.json"

  override protected val transSMARTDataFileName = "denopa-firstvisit_data_file"

  override protected val transSMARTMappingFileName = "denopa-firstvisit_mapping_file"

  override protected def router = DataSetRouter(
    routes.DeNoPaFirstVisitController.find,
    routes.DeNoPaFirstVisitController.find(),
    routes.DeNoPaFirstVisitController.get,
    routes.DeNoPaFirstVisitController.exportRecordsAsCsv(),
    routes.DeNoPaFirstVisitController.exportRecordsAsJson(),
    routes.DeNoPaFirstVisitController.exportTranSMARTDataFile(),
    routes.DeNoPaFirstVisitController.exportTranSMARTMappingFile()
  )
}