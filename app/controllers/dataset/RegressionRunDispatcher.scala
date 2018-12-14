package controllers.dataset

import javax.inject.Inject

import org.incal.play.controllers.SecureControllerDispatcher
import org.incal.core.FilterCondition
import org.incal.play.security.SecurityRole
import reactivemongo.bson.BSONObjectID
import models.security.DataSetPermission
import org.incal.spark_ml.models.results.RegressionSetting

class RegressionRunDispatcher @Inject()(dscf: DataSetControllerFactory, crcf: RegressionRunControllerFactory)
  extends SecureControllerDispatcher[RegressionRunController]("dataSet") with RegressionRunController {

  override protected def getController(id: String) =
    dscf(id).map(_ => crcf(id)).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array(SecurityRole.admin))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(DataSetPermission(controllerId, ControllerName.regressionRun, actionName))

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def regress(
    setting: RegressionSetting,
    saveResults: Boolean
  ) = dispatch(_.regress(setting, saveResults))

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