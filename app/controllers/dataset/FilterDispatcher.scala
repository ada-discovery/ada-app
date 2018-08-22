package controllers.dataset

import javax.inject.Inject

import controllers.core.AdminOrOwnerControllerDispatcherExt
import org.incal.play.controllers.SecureControllerDispatcher
import models.security.UserManager
import models.{AdaException, Filter}
import persistence.dataset.DataSetAccessorFactory
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID
import security.AdaAuthConfig
import models.security.DataSetPermission
import org.incal.core.FilterCondition
import org.incal.play.security.SecurityRole

import scala.concurrent.ExecutionContext.Implicits.global

class FilterDispatcher @Inject()(
    dscf: DataSetControllerFactory,
    fcf: FilterControllerFactory,
    dsaf: DataSetAccessorFactory,
    val userManager: UserManager
  ) extends SecureControllerDispatcher[FilterController]("dataSet")
      with AdminOrOwnerControllerDispatcherExt[FilterController]
      with FilterController
      with AdaAuthConfig {

  override protected def getController(id: String) =
    dscf(id).map(_ => fcf(id)).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array(SecurityRole.admin))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(DataSetPermission(controllerId, ControllerName.filter, actionName))

  override def get(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.update(id))

  override def edit(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.edit(id))

  override def delete(id: BSONObjectID) = dispatchIsAdminOrOwner(id, _.delete(id))

  override def save = dispatch(_.save)

  override def saveAjax(filter: Filter) = dispatchAjax(_.saveAjax(filter))

  override def idAndNames = dispatchIsAdmin(_.idAndNames)

  override def idAndNamesAccessible  = dispatchAjax(_.idAndNamesAccessible)

  protected def dispatchIsAdminOrOwner(
    id: BSONObjectID,
    action: FilterController => Action[AnyContent]
  ): Action[AnyContent] = {

    val objectOwnerFun = {
      request: Request[AnyContent] =>
        val dataSetId = getControllerId(request)
        val dsa = dsaf(dataSetId).getOrElse(throw new AdaException(s"Data set id $dataSetId not found."))
        dsa.filterRepo.get(id).map { filter =>
          filter.flatMap(_.createdById)
        }
    }

    dispatchIsAdminOrOwnerAux(objectOwnerFun, None)(action)
  }
}