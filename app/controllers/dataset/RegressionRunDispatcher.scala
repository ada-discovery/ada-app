package controllers.dataset

import javax.inject.Inject

import controllers.SecureControllerDispatcher
import models.FilterCondition
import models.ml.RegressionSetting
import reactivemongo.bson.BSONObjectID
import util.SecurityUtil.createDataSetPermission

class RegressionRunDispatcher @Inject()(dscf: DataSetControllerFactory, crcf: RegressionRunControllerFactory)
  extends SecureControllerDispatcher[RegressionRunController]("dataSet") with RegressionRunController {

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

  override def regress(
    setting: RegressionSetting,
    saveResults: Boolean
  ) = dispatch(_.regress(setting, saveResults))

  override def delete(id: BSONObjectID) = dispatch(_.delete(id))
}