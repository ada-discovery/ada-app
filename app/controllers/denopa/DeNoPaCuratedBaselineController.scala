package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.DataSetRouter
import persistence.DictionaryFieldRepo

class DeNoPaCuratedBaselineController @Inject() (
    @Named("DeNoPaCuratedBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DeNoPaController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa Curated Baseline"

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"))

  override protected val overviewFieldNamesConfPrefix = "denopa.curatedbaseline"

  override protected def router = DataSetRouter(
    routes.DeNoPaCuratedBaselineController.find,
    routes.DeNoPaCuratedBaselineController.find(),
    routes.DeNoPaCuratedBaselineController.get,
    routes.DeNoPaCuratedBaselineController.exportRecordsAsCsv(),
    routes.DeNoPaCuratedBaselineController.exportRecordsAsJson(),
    routes.DeNoPaCuratedBaselineController.exportTranSMARTDataFile(),
    routes.DeNoPaCuratedBaselineController.exportTranSMARTMappingFile(),
    routes.DeNoPaCuratedBaselineController.getScatterStats()
  )
}