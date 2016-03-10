package controllers.dataset.denopa

import controllers.dataset.DataSetControllerImpl
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json._

protected abstract class DenopaController(
    dataSetId: String,
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DataSetControllerImpl(dataSetId, dsaf, dataSetMetaInfoRepo) {

  override protected val keyField = "Probanden_Nr"
  override protected val exportOrderByField = "Line_Nr"
  override protected val defaultScatterXFieldName = "a_Alter"
  override protected val defaultDistributionFieldName = "a_Alter"

  private val mmstSumField = "a_CRF_MMST_Summe"
  private val mmstCognitiveCategoryField = "a_CRF_MMST_Category"

  // Ad-hoc extension requested by Venkata, currently not used
  // TODO: remove
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