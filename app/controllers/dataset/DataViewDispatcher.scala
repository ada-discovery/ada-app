package controllers.dataset

import javax.inject.Inject

import controllers.SecureControllerDispatcher
import dataaccess.User
import models.security.{SecurityRole, UserManager}
import models.{AdaException, FilterCondition}
import persistence.dataset.DataSetAccessorFactory
import play.api.mvc.{Action, AnyContent, Request, Result}
import reactivemongo.bson.BSONObjectID
import security.AdaAuthConfig
import util.SecurityUtil.{createDataSetPermission, restrictAdmin, restrictChainFuture}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DataViewDispatcher @Inject()(
    dscf: DataSetControllerFactory,
    dvc: DataViewControllerFactory,
    dsaf: DataSetAccessorFactory,
    val userManager: UserManager
  ) extends SecureControllerDispatcher[DataViewController]("dataSet") with DataViewController with AdaAuthConfig {

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

  override def get(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.update(id))

  override def edit(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.edit(id))

  override def delete(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.delete(id))

  override def save = dispatch(_.save)

  override def idAndNames = dispatch(_.idAndNames)

  override def getAndShowView(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.getAndShowView(id))

  override def updateAndShowView(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.updateAndShowView(id))

  override def copy(id: BSONObjectID) = dispatch(_.copy(id))

  override def addDistributions(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwner(dataViewId, _.addDistributions(dataViewId, fieldNames))

  override def addDistribution(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = dispatchIsAdminOrOwner(dataViewId, _.addDistribution(dataViewId, fieldName, groupFieldName))

  override def addCumulativeCounts(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwner(dataViewId, _.addCumulativeCounts(dataViewId, fieldNames))

  override def addCumulativeCount(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ) = dispatchIsAdminOrOwner(dataViewId, _.addCumulativeCount(dataViewId, fieldName, groupFieldName))

  override def addTableFields(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwner(dataViewId, _.addTableFields(dataViewId, fieldNames))

  override def addCorrelation(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwner(dataViewId, _.addCorrelation(dataViewId, fieldNames))

  override def addScatter(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    groupFieldName: Option[String]
  ) = dispatchIsAdminOrOwner(dataViewId, _.addScatter(dataViewId, xFieldName, yFieldName, groupFieldName))

  override def addBoxPlots(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwner(dataViewId, _.addBoxPlots(dataViewId, fieldNames))

  override def addBasicStats(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ) = dispatchIsAdminOrOwner(dataViewId, _.addBasicStats(dataViewId, fieldNames))

  override def saveFilter(
    dataViewId: BSONObjectID,
    filterOrIds: Seq[Either[Seq[models.FilterCondition], BSONObjectID]]
  ) = dispatchIsAdminOrOwner(dataViewId, _.saveFilter(dataViewId, filterOrIds))

  protected def dispatchIsAdminOrOwner(
    id: BSONObjectID,
    action: DataViewController => Action[AnyContent]
  ): Action[AnyContent] = {
    val currentUserFun = {
      request: Request[_] => currentUser(request)
    }

    val objectOwnerFun = {
      request: Request[AnyContent] =>
        val dataSetId = getControllerId(request)
        val dsa = dsaf(dataSetId).getOrElse(throw new AdaException(s"Data set id $dataSetId not found."))
        dsa.dataViewRepo.get(id).map { dataView =>
          dataView.flatMap(_.createdById)
        }
      }

    dispatchIsAdminOrOwnerAux(objectOwnerFun, currentUserFun)(action)
  }
}