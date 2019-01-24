package controllers.dataset

import org.incal.core.FilterCondition
import org.incal.play.controllers.ReadonlyController
import org.incal.spark_ml.models.setting.ClassificationRunSpec
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

trait ClassificationRunController extends ReadonlyController[BSONObjectID]{

  def create: Action[AnyContent]

  def classify(
    setting: ClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ): Action[AnyContent]

  def selectFeaturesAsAnovaChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int
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