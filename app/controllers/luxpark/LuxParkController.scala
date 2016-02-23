package controllers.luxpark

import javax.inject.{Inject, Named}

import controllers.DataSetControllerImpl
import models.Category
import org.apache.commons.lang3.StringEscapeUtils
import persistence.DictionaryFieldRepo
import services.RedCapService

import scala.concurrent.Await

class LuxParkController @Inject() (
    @Named("LuxParkDictionaryRepo") dictionaryRepo: DictionaryFieldRepo,
    redCapService: RedCapService
  ) extends DataSetControllerImpl(dictionaryRepo) {

  override val dataSetId = "luxpark"

  override protected val dataSetName = "LuxPark"

  override protected val keyField = "cdisc_dm_usubjd"

  override protected val exportOrderByField = "cdisc_dm_usubjd"

  override protected val listViewColumns = Some(Seq("cdisc_dm_usubjd", "redcap_event_name", "cdisc_dm_subjid_2", "dm_site", "cdisc_dm_brthdtc", "cdisc_dm_sex", "cdisc_sc_sctestcd_maritstat"))

  override protected val overviewFieldNamesConfPrefix = "luxpark"

  override protected val defaultScatterXFieldName = "digitsf_score"

  override protected val defaultScatterYFieldName = "bentons_score"

  override protected val defaultDistributionFieldName = "digitsf_score"

  private val visitField = Some("redcap_event_name")

  /**
    * Generate  content of TRANSMART data file for download.
    *
    * @param dataFilename Name of output file.
    * @param delimiter Delimiter for output file.
    * @param orderBy Order of fields in data file.
    * @return VString with file content.
    */
  override protected def generateTranSMARTDataFile(dataFilename: String, delimiter: String, orderBy : String): String = {
    val recordsFuture = repo.find(None, toSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val metadataFuture = redCapService.listMetadatas("field_name", "")
    val metadatas = Await.result(metadataFuture, timeout)

    // categories
    val rootCategory = new Category("")
    val categories = metadatas.map(_.form_name).toSet.map { formName : String =>
      new Category(formName)
    }.toList

    rootCategory.addChildren(categories)
    val nameCategoryMap = categories.map(category => (category.name, category)).toMap

    // field category map
    val fieldCategoryMap = metadatas.map{metadata =>
      (metadata.field_name, nameCategoryMap.get(metadata.form_name).get)
    }.toMap

    val replacements = List(("\r", " "), ("\n", " "))
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    tranSMARTService.createClinicalDataFile(unescapedDelimiter , "\n",replacements)(
      records, keyField, visitField, fieldCategoryMap)
  }

  /**
    * Generate the content of TRANSMART mapping file for downnload.
    *
    * @param dataFilename Name of output file.
    * @param delimiter Delimiter for output file.
    * @param orderBy Order of fields in data file.
    * @return VString with file content.
    */
  override protected def generateTranSMARTMappingFile(dataFilename: String, delimiter: String, orderBy : String): String = {
    val recordsFuture = repo.find(None, toSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)

    val fieldsFuture = dictionaryRepo.find()
    val fields = Await.result(fieldsFuture, timeout)

    // field label map
    val fieldLabelMap = fields.map{field =>
      (field.name, field.label.getOrElse(field.name))
    }.toMap

    val metadataFuture = redCapService.listMetadatas("field_name", "")
    val metadatas = Await.result(metadataFuture, timeout)

    // categories
    val rootCategory = new Category("")
    val categories = metadatas.map(_.form_name).toSet.map { formName : String =>
      new Category(formName)
    }.toList

    rootCategory.addChildren(categories)
    val nameCategoryMap = categories.map(category => (category.name, category)).toMap

    // field category map
    val fieldCategoryMap = metadatas.map{metadata =>
      (metadata.field_name, nameCategoryMap.get(metadata.form_name).get)
    }.toMap

    val replacements = List(("\r", " "), ("\n", " "))
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    tranSMARTService.createMappingFile(unescapedDelimiter , "\n", replacements)(
      records, dataFilename, keyField, visitField, fieldCategoryMap, rootCategory, fieldLabelMap)
  }
}