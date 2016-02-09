package controllers

import controllers.denopa.{routes => denoparoutes}
import controllers.luxpark.{routes => luxparkroutes}

object StudyRouter extends Enumeration {
  case class StudyRouter(dataSetRouter: DataSetRouter, dictionaryRouter: DictionaryRouter) extends super.Val

  val DeNoPaBaseline = StudyRouter(
    DataSetRouter(
      denoparoutes.DeNoPaBaselineController.find,
      denoparoutes.DeNoPaBaselineController.find(),
      denoparoutes.DeNoPaBaselineController.overviewList,
      denoparoutes.DeNoPaBaselineController.overviewList(),
      denoparoutes.DeNoPaBaselineController.get,
      denoparoutes.DeNoPaBaselineController.exportAllRecordsAsCsv(),
      denoparoutes.DeNoPaBaselineController.exportAllRecordsAsJson(),
      denoparoutes.DeNoPaBaselineController.exportRecordsAsCsv(),
      denoparoutes.DeNoPaBaselineController.exportRecordsAsJson(),
      denoparoutes.DeNoPaBaselineController.exportTranSMARTDataFile(),
      denoparoutes.DeNoPaBaselineController.exportTranSMARTMappingFile(),
      denoparoutes.DeNoPaBaselineController.getScatterStats()
    ),
    DictionaryRouter(
      denoparoutes.DeNoPaBaselineDictionaryController.overviewList,
      denoparoutes.DeNoPaBaselineDictionaryController.overviewList(),
      denoparoutes.DeNoPaBaselineDictionaryController.get,
      denoparoutes.DeNoPaBaselineDictionaryController.save,
      denoparoutes.DeNoPaBaselineDictionaryController.update
    )
  )

  val DeNoPaFirstVisit = StudyRouter(
    DataSetRouter(
      denoparoutes.DeNoPaFirstVisitController.find,
      denoparoutes.DeNoPaFirstVisitController.find(),
      denoparoutes.DeNoPaFirstVisitController.overviewList,
      denoparoutes.DeNoPaFirstVisitController.overviewList(),
      denoparoutes.DeNoPaFirstVisitController.get,
      denoparoutes.DeNoPaFirstVisitController.exportAllRecordsAsCsv(),
      denoparoutes.DeNoPaFirstVisitController.exportAllRecordsAsJson(),
      denoparoutes.DeNoPaFirstVisitController.exportRecordsAsCsv(),
      denoparoutes.DeNoPaFirstVisitController.exportRecordsAsJson(),
      denoparoutes.DeNoPaFirstVisitController.exportTranSMARTDataFile(),
      denoparoutes.DeNoPaFirstVisitController.exportTranSMARTMappingFile(),
      denoparoutes.DeNoPaFirstVisitController.getScatterStats()
    ),
    DictionaryRouter(
      denoparoutes.DeNoPaFirstVisitDictionaryController.overviewList,
      denoparoutes.DeNoPaFirstVisitDictionaryController.overviewList(),
      denoparoutes.DeNoPaFirstVisitDictionaryController.get,
      denoparoutes.DeNoPaFirstVisitDictionaryController.save,
      denoparoutes.DeNoPaFirstVisitDictionaryController.update
    )
  )

  val DeNoPaCuratedBaseline = StudyRouter(
    DataSetRouter(
      denoparoutes.DeNoPaCuratedBaselineController.find,
      denoparoutes.DeNoPaCuratedBaselineController.find(),
      denoparoutes.DeNoPaCuratedBaselineController.overviewList,
      denoparoutes.DeNoPaCuratedBaselineController.overviewList(),
      denoparoutes.DeNoPaCuratedBaselineController.get,
      denoparoutes.DeNoPaCuratedBaselineController.exportAllRecordsAsCsv(),
      denoparoutes.DeNoPaCuratedBaselineController.exportAllRecordsAsJson(),
      denoparoutes.DeNoPaCuratedBaselineController.exportRecordsAsCsv(),
      denoparoutes.DeNoPaCuratedBaselineController.exportRecordsAsJson(),
      denoparoutes.DeNoPaCuratedBaselineController.exportTranSMARTDataFile(),
      denoparoutes.DeNoPaCuratedBaselineController.exportTranSMARTMappingFile(),
      denoparoutes.DeNoPaCuratedBaselineController.getScatterStats()
    ),
    DictionaryRouter(
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.overviewList,
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.overviewList(),
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.get,
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.save,
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.update
    )
  )

  val DeNoPaCuratedFirstVisit = StudyRouter(
    DataSetRouter(
      denoparoutes.DeNoPaCuratedFirstVisitController.find,
      denoparoutes.DeNoPaCuratedFirstVisitController.find(),
      denoparoutes.DeNoPaCuratedFirstVisitController.overviewList,
      denoparoutes.DeNoPaCuratedFirstVisitController.overviewList(),
      denoparoutes.DeNoPaCuratedFirstVisitController.get,
      denoparoutes.DeNoPaCuratedFirstVisitController.exportAllRecordsAsCsv(),
      denoparoutes.DeNoPaCuratedFirstVisitController.exportAllRecordsAsJson(),
      denoparoutes.DeNoPaCuratedFirstVisitController.exportRecordsAsCsv(),
      denoparoutes.DeNoPaCuratedFirstVisitController.exportRecordsAsJson(),
      denoparoutes.DeNoPaCuratedFirstVisitController.exportTranSMARTDataFile(),
      denoparoutes.DeNoPaCuratedFirstVisitController.exportTranSMARTMappingFile(),
      denoparoutes.DeNoPaCuratedFirstVisitController.getScatterStats()
    ),
    DictionaryRouter(
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.overviewList,
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.overviewList(),
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.get,
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.save,
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.update
    )
  )

  val LuxPark = StudyRouter(
    DataSetRouter(
      luxparkroutes.LuxParkController.find,
      luxparkroutes.LuxParkController.find(),
      luxparkroutes.LuxParkController.overviewList,
      luxparkroutes.LuxParkController.overviewList(),
      luxparkroutes.LuxParkController.get,
      luxparkroutes.LuxParkController.exportAllRecordsAsCsv(),
      luxparkroutes.LuxParkController.exportAllRecordsAsJson(),
      luxparkroutes.LuxParkController.exportRecordsAsCsv(),
      luxparkroutes.LuxParkController.exportRecordsAsJson(),
      luxparkroutes.LuxParkController.exportTranSMARTDataFile(),
      luxparkroutes.LuxParkController.exportTranSMARTMappingFile(),
      luxparkroutes.LuxParkController.getScatterStats()
    ),
    DictionaryRouter(
      luxparkroutes.LuxParkDictionaryController.overviewList,
      luxparkroutes.LuxParkDictionaryController.overviewList(),
      luxparkroutes.LuxParkDictionaryController.get,
      luxparkroutes.LuxParkDictionaryController.save,
      luxparkroutes.LuxParkDictionaryController.update
    )
  )
}
