package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.DataSetRouter
import persistence.DictionaryFieldRepo

class DeNoPaBaselineController @Inject() (
    @Named("DeNoPaBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DeNoPaController(dictionaryRepo) {

  override protected def showTitle = "Baseline Item"

  override protected def listTitle = "baseline item"

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"))

  override protected val csvFileName = "denopa-baseline.csv"

  override protected val jsonFileName = "denopa-baseline.json"

  override protected val transSMARTDataFileName = "denopa-baseline_data_file"

  override protected val transSMARTMappingFileName = "denopa-baseline_mapping_file"

  override protected def router = DataSetRouter(
    routes.DeNoPaBaselineController.find,
    routes.DeNoPaBaselineController.find(),
    routes.DeNoPaBaselineController.get,
    routes.DeNoPaBaselineController.exportRecordsAsCsv(),
    routes.DeNoPaBaselineController.exportRecordsAsJson(),
    routes.DeNoPaBaselineController.exportTranSMARTDataFile(),
    routes.DeNoPaBaselineController.exportTranSMARTMappingFile()
  )
}