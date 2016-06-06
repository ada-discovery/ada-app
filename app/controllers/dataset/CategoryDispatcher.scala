package controllers.dataset

import javax.inject.Inject

import controllers.{SecureControllerDispatcher, ControllerDispatcher}
import reactivemongo.bson.BSONObjectID
import util.FilterSpec
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

  override def find(page: Int, orderBy: String, filter: FilterSpec) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: Int) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: BSONObjectID) = dispatch(_.update(id))

  override def edit(id: BSONObjectID) = dispatch(_.edit(id))

  override def delete(id: BSONObjectID) = dispatch(_.delete(id))

  override def save = dispatch(_.save)

  override def getCategoryD3Root = dispatch(_.getCategoryD3Root)

  override def relocateToParent(id: BSONObjectID, parentId: Option[BSONObjectID]) = dispatch(_.relocateToParent(id, parentId))

  override def jsRoutes = dispatch(_.jsRoutes)
}