package controllers.denopa

import javax.inject.Inject

import controllers.{DataSetController, ReadonlyController, ExportableAction}
import org.apache.commons.lang3.StringEscapeUtils
import persistence.{DictionaryFieldRepo, AsyncReadonlyRepo}
import play.api.mvc.Action
import util.WebExportUtil.stringToFile
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaTranSMARTMapping._
import services.TranSMARTService

import scala.concurrent.Await

protected abstract class DeNoPaController(dictionaryRepo: DictionaryFieldRepo) extends DataSetController(dictionaryRepo) {

  protected override val keyField = "Probanden_Nr"
  protected override val exportOrderByField = "Line_Nr"

  private val mmstSumField = "a_CRF_MMST_Summe"
  private val mmstCognitiveCategoryField = "a_CRF_MMST_Category"

  override protected def getTranSMARTDataAndMappingFiles(dataFilename: String, delimiter: String, orderBy : String) = {
    val recordsFuture = repo.find(None, toJsonSort(orderBy), None, None, None)
    val records = Await.result(recordsFuture, timeout)
    val extendedRecords = getExtendedRecords(records)

    val unescapedDelimiter = StringEscapeUtils.unescapeJava(delimiter)
    tranSMARTService.createClinicalDataAndMappingFiles(unescapedDelimiter , "\n", List[(String, String)]())(extendedRecords.toList, dataFilename, keyField, None, fieldCategoryMap, rootCategory, fieldLabelMap)
  }

  // Ad-hoc extension requested by Venkata
  private def getExtendedRecords(records : Traversable[JsObject]) =
    records.map{ record =>
      val mmstSum = (record \ mmstSumField).toOption
      if (mmstSum.isDefined) {
        val category = if (mmstSum.get == JsNull) {
          null
        } else {
          val sum = mmstSum.get.asOpt[Int]
          if (!sum.isDefined)
            null
          else
            sum.get match {
              case x if x <= 9 => "Severe"
              case x if ((x >= 10) && (x <= 18)) => "Moderate"
              case x if ((x >= 19) && (x <= 24)) => "Mild"
              case x if ((x >= 25) && (x <= 26)) => "Sub-Normal"
              case x if ((x >= 27) && (x <= 30)) => "Normal"
            }
        }
        record + (mmstCognitiveCategoryField -> Json.toJson(category))
      } else
        record
  }
}