package controllers.dataset

import javax.inject.Inject

import controllers.SecureControllerDispatcher
import models.FilterCondition
import play.api.mvc.{AnyContent, Action}
import reactivemongo.bson.BSONObjectID
import models.Filter
import util.SecurityUtil.createDataSetPermission

class FilterDispatcher @Inject()(dscf: DataSetControllerFactory, fcf: FilterControllerFactory)
  extends SecureControllerDispatcher[FilterController]("dataSet") with FilterController {

  override protected def getController(id: String) =
    dscf(id).map(_ => fcf(id)).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array("admin"))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(createDataSetPermission(controllerId, "filter", actionName))

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatch(_.update(id))

  override def edit(id: BSONObjectID) = dispatch(_.edit(id))

  override def delete(id: BSONObjectID) = dispatch(_.delete(id))

  override def save = dispatch(_.save)

  override def saveAjax(filter: Filter): Action[AnyContent] = dispatch(_.saveAjax(filter))

  override def getIdAndNames: Action[AnyContent] = dispatch(_.getIdAndNames)
}