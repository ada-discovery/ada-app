package controllers.dataset

import javax.inject.Inject

import controllers.{SecureControllerDispatcher, ControllerDispatcher}
import models.FilterCondition
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID
import util.SecurityUtil.createDataSetPermission

class CategoryDispatcher @Inject()(dscf: DataSetControllerFactory, ccf: CategoryControllerFactory)
  extends SecureControllerDispatcher[CategoryController]("dataSet") with CategoryController {

  override protected def getController(id: String) =
    dscf(id).map(_ => ccf(id)).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array("admin"))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(createDataSetPermission(controllerId, "category", actionName))

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatch(_.update(id))

  override def edit(id: BSONObjectID) = dispatch(_.edit(id))

  override def delete(id: BSONObjectID) = dispatch(_.delete(id))

  override def save = dispatch(_.save)

  override def saveForName(name: String) = dispatch(_.saveForName(name))

  override def getCategoryD3Root = dispatch(_.getCategoryD3Root)

  override def relocateToParent(id: BSONObjectID, parentId: Option[BSONObjectID]) = dispatch(_.relocateToParent(id, parentId))

  override def idAndNames = dispatch(_.idAndNames)

  override def addFields(categoryId: BSONObjectID, fieldNames: Seq[String]) = dispatch(_.addFields(categoryId, fieldNames))

  override def jsRoutes = dispatch(_.jsRoutes)
}