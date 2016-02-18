package controllers

import controllers.denopa.{routes => denoparoutes}
import controllers.luxpark.{routes => luxparkroutes}

object StudyRouter extends Enumeration {
  case class StudyRouterHolder(order: Int, name: String, dataSetRouter: DataSetRouter, dictionaryRouter: DictionaryRouter) extends super.Val
  implicit def valueToHolder(x: Value) = x.asInstanceOf[StudyRouterHolder]

  val LuxPark = StudyRouterHolder(
    0,
    "LuxPark",
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
      luxparkroutes.LuxParkController.getScatterStats(),
      luxparkroutes.LuxParkController.getDistribution(),
      luxparkroutes.LuxParkDictionaryController.getFieldNames()
    ),
    DictionaryRouter(
      luxparkroutes.LuxParkDictionaryController.overviewList,
      luxparkroutes.LuxParkDictionaryController.overviewList(),
      luxparkroutes.LuxParkDictionaryController.get,
      luxparkroutes.LuxParkDictionaryController.save,
      luxparkroutes.LuxParkDictionaryController.update
    )
  )

  val DeNoPaCuratedBaseline = StudyRouterHolder(
    1,
    "DeNoPa Curated Baseline",
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
      denoparoutes.DeNoPaCuratedBaselineController.getScatterStats(),
      denoparoutes.DeNoPaCuratedBaselineController.getDistribution(),
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.getFieldNames()
    ),
    DictionaryRouter(
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.overviewList,
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.overviewList(),
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.get,
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.save,
      denoparoutes.DeNoPaCuratedBaselineDictionaryController.update
    )
  )

  val DeNoPaCuratedFirstVisit = StudyRouterHolder(
    2,
    "DeNoPa Curated First Visit",
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
      denoparoutes.DeNoPaCuratedFirstVisitController.getScatterStats(),
      denoparoutes.DeNoPaCuratedFirstVisitController.getDistribution(),
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.getFieldNames()
    ),
    DictionaryRouter(
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.overviewList,
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.overviewList(),
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.get,
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.save,
      denoparoutes.DeNoPaCuratedFirstVisitDictionaryController.update
    )
  )

  val DeNoPaBaseline = StudyRouterHolder(
    3,
    "DeNoPa Baseline",
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
      denoparoutes.DeNoPaBaselineController.getScatterStats(),
      denoparoutes.DeNoPaBaselineController.getDistribution(),
      denoparoutes.DeNoPaBaselineDictionaryController.getFieldNames()
    ),
    DictionaryRouter(
      denoparoutes.DeNoPaBaselineDictionaryController.overviewList,
      denoparoutes.DeNoPaBaselineDictionaryController.overviewList(),
      denoparoutes.DeNoPaBaselineDictionaryController.get,
      denoparoutes.DeNoPaBaselineDictionaryController.save,
      denoparoutes.DeNoPaBaselineDictionaryController.update
    )
  )

  val DeNoPaFirstVisit = StudyRouterHolder(
    4,
    "DeNoPa First Visit",
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
      denoparoutes.DeNoPaFirstVisitController.getScatterStats(),
      denoparoutes.DeNoPaFirstVisitController.getDistribution(),
      denoparoutes.DeNoPaFirstVisitDictionaryController.getFieldNames()
    ),
    DictionaryRouter(
      denoparoutes.DeNoPaFirstVisitDictionaryController.overviewList,
      denoparoutes.DeNoPaFirstVisitDictionaryController.overviewList(),
      denoparoutes.DeNoPaFirstVisitDictionaryController.get,
      denoparoutes.DeNoPaFirstVisitDictionaryController.save,
      denoparoutes.DeNoPaFirstVisitDictionaryController.update
    )
  )
}