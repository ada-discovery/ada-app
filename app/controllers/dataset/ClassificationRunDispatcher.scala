package controllers.dataset

import javax.inject.Inject

import controllers.SecureControllerDispatcher
import models.FilterCondition
import models.ml.ClassificationSetting
import reactivemongo.bson.BSONObjectID
import models.ml.ClassificationEvalMetric
import play.api.mvc.{Action, AnyContent}
import util.SecurityUtil.createDataSetPermission

class ClassificationRunDispatcher @Inject()(dscf: DataSetControllerFactory, crcf: ClassificationRunControllerFactory)
  extends SecureControllerDispatcher[ClassificationRunController]("dataSet") with ClassificationRunController {

  override protected def getController(id: String) =
    dscf(id).map(_ => crcf(id)).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array("admin"))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(createDataSetPermission(controllerId, "classificationRun", actionName))

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def classify(
    setting: ClassificationSetting,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ) = dispatch(_.classify(setting, saveResults, saveBinCurves))

  override def selectFeaturesAsChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int,
    discretizerBucketsNum: Int
  ) = dispatch(_.selectFeaturesAsChiSquare(inputFieldNames, outputFieldName, filterId, featuresToSelectNum, discretizerBucketsNum))

  override def selectFeaturesAsAnovaChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int
  ) = dispatch(_.selectFeaturesAsAnovaChiSquare(inputFieldNames, outputFieldName, filterId, featuresToSelectNum))

  override def delete(id: BSONObjectID) = dispatch(_.delete(id))

  override def exportToDataSet(
    targetDataSetId: Option[String],
    targetDataSetName: Option[String]
  ) = dispatch(_.exportToDataSet(targetDataSetId, targetDataSetName))

  override def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsCsv(delimiter, replaceEolWithSpace, eol, filter, tableColumnsOnly))

  def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsJson(filter, tableColumnsOnly))
}