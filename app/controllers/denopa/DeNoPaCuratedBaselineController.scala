package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.DataSetRouter
import persistence.DictionaryFieldRepo

class DeNoPaCuratedBaselineController @Inject() (
    @Named("DeNoPaCuratedBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DeNoPaController(dictionaryRepo) {

  override protected def showTitle = "Curated Baseline Item"

  override protected def listTitle = "curated baseline item"

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"))

  override protected val csvFileName = "denopa-curated-baseline.csv"

  override protected val jsonFileName = "denopa-curated-baseline.json"

  override protected val transSMARTDataFileName = "denopa-curated-baseline_data_file"

  override protected val transSMARTMappingFileName = "denopa-curated-baseline_mapping_file"

  override protected def router = DataSetRouter(
    routes.DeNoPaCuratedBaselineController.find,
    routes.DeNoPaCuratedBaselineController.find(),
    routes.DeNoPaCuratedBaselineController.get,
    routes.DeNoPaCuratedBaselineController.exportRecordsAsCsv(),
    routes.DeNoPaCuratedBaselineController.exportRecordsAsJson(),
    routes.DeNoPaCuratedBaselineController.exportTranSMARTDataFile(),
    routes.DeNoPaCuratedBaselineController.exportTranSMARTMappingFile()
  )
}