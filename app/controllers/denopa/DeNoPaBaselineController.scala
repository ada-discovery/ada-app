package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.DataSetRouter
import persistence.DictionaryFieldRepo

class DeNoPaBaselineController @Inject() (
    @Named("DeNoPaBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DeNoPaController(dictionaryRepo) {

  override protected val showTitle = "Baseline Item"

  override protected val listTitle = "baseline item"

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"))

  override protected val csvFileName = "denopa-baseline.csv"

  override protected val jsonFileName = "denopa-baseline.json"

  override protected val transSMARTDataFileName = "denopa-baseline_data_file"

  override protected val transSMARTMappingFileName = "denopa-baseline_mapping_file"

  override protected val overviewFiledNamesConfPrefix = "denopa.baseline"

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