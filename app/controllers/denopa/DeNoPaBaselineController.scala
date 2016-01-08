package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.DataSetRouter
import persistence.DictionaryFieldRepo

class DeNoPaBaselineController @Inject() (
    @Named("DeNoPaBaselineDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DeNoPaController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa Baseline"

  override protected val listViewColumns = Some(List("Line_Nr", "Probanden_Nr", "Geb_Datum", "a_Gruppe", "b_Gruppe"))

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