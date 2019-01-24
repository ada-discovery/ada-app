package controllers.dataset

import org.incal.core.FilterCondition
import org.incal.play.controllers.ReadonlyController
import org.incal.spark_ml.models.setting.RegressionRunSpec
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait RegressionRunController extends ReadonlyController[BSONObjectID]{

  def create: Action[AnyContent]

  def regress(
    setting: RegressionRunSpec,
    saveResults: Boolean
  ): Action[AnyContent]

  def delete(id: BSONObjectID): Action[AnyContent]

  def exportToDataSet(
    targetDataSetId: Option[String],
    targetDataSetName: Option[String]
  ): Action[AnyContent]

  def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ): Action[AnyContent]

  def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ): Action[AnyContent]
}