package controllers

import javax.inject.{Named, Inject}

import org.apache.commons.lang3.StringEscapeUtils
import persistence.DictionaryFieldRepo
import play.api.libs.json._
import services.DeNoPaTranSMARTMapping._

import scala.concurrent.Await

class LuxParkController @Inject()(
  @Named("LuxParkDictionaryRepo") dictionaryRepo: DictionaryFieldRepo) extends DataSetController(dictionaryRepo) {

  override protected val keyField = "cdisc_dm_usubjd"

  override protected val exportOrderByField = "cdisc_dm_usubjd"

  override protected val dataSetName = "LuxPark"

  override protected val listViewColumns = Some(List("cdisc_dm_usubjd", "redcap_event_name", "cdisc_dm_subjid_2", "dm_site", "cdisc_dm_brthdtc", "cdisc_dm_sex", "cdisc_sc_sctestcd_maritstat"))

  override protected val overviewFieldNamesConfPrefix = "luxpark"

  override protected def getTranSMARTDataAndMappingFiles(dataFilename: String, delimiter: String, orderBy : String) = {
    val recordsFuture = repo.find(None, toSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    // TODO: obtain categories and labels from RedCap service
    tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", List[(String, String)]())(
      records.toList, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)
  }

  override protected def router = DataSetRouter(
    routes.LuxParkController.find,
    routes.LuxParkController.find(),
    routes.LuxParkController.overviewList,
    routes.LuxParkController.overviewList(),
    routes.LuxParkController.get,
    routes.LuxParkController.exportAllRecordsAsCsv(),
    routes.LuxParkController.exportAllRecordsAsJson(),
    routes.LuxParkController.exportRecordsAsCsv(),
    routes.LuxParkController.exportRecordsAsJson(),
    routes.LuxParkController.exportTranSMARTDataFile(),
    routes.LuxParkController.exportTranSMARTMappingFile(),
    routes.LuxParkController.getScatterStats(),
    routes.LuxParkController.dictionary,
    routes.LuxParkController.dictionary(),
    routes.LuxParkController.getField
  )
}