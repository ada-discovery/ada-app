package controllers.dataset

import javax.inject.Inject

import controllers.SecureControllerDispatcher
import models.FilterCondition
import play.api.mvc.{AnyContent, Action}
import reactivemongo.bson.BSONObjectID
import util.SecurityUtil.createDataSetPermission

class DataViewDispatcher @Inject()(dscf: DataSetControllerFactory, dvc: DataViewControllerFactory)
  extends SecureControllerDispatcher[DataViewController]("dataSet") with DataViewController {

  override protected def getController(id: String) =
    dscf(id).map(_ => dvc(id)).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array("admin"))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(createDataSetPermission(controllerId, "dataview", actionName))

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatch(_.update(id))

  override def edit(id: BSONObjectID) = dispatch(_.edit(id))

  override def delete(id: BSONObjectID) = dispatch(_.delete(id))

  override def save = dispatch(_.save)

  override def idAndNames = dispatch(_.idAndNames)

  override def getAndShowView(id: BSONObjectID) = dispatch(_.getAndShowView(id))

  override def updateAndShowView(id: BSONObjectID) = dispatch(_.updateAndShowView(id))

  override def copy(id: BSONObjectID) = dispatch(_.copy(id))

  override def addDistributions(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatch(_.addDistributions(dataViewId, fieldNames))

  override def addDistribution(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = dispatch(_.addDistribution(dataViewId, fieldName, groupFieldName))

  override def addTableFields(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatch(_.addTableFields(dataViewId, fieldNames))

  override def addCorrelation(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatch(_.addCorrelation(dataViewId, fieldNames))

  override def addScatter(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    groupFieldName: Option[String]
  ) = dispatch(_.addScatter(dataViewId, xFieldName, yFieldName, groupFieldName))

  override def addBoxPlots(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatch(_.addBoxPlots(dataViewId, fieldNames))

  override def saveFilter(
    dataViewId: BSONObjectID,
    filterOrIds: Seq[Either[Seq[models.FilterCondition], BSONObjectID]]
  ) = dispatch(_.saveFilter(dataViewId, filterOrIds))
}