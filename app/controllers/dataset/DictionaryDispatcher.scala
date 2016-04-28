package controllers.dataset

import controllers.{SecureControllerDispatcher, ControllerDispatcher}
import util.FilterSpec
import javax.inject.Inject

class DictionaryDispatcher @Inject() (dscf: DataSetControllerFactory, dcf: DictionaryControllerFactory)
  extends SecureControllerDispatcher[DictionaryController]("dataSet") with DictionaryController {

  override protected def getController(id: String) =
    dscf(id).map(_ => dcf(id)).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  // TODO: here we need to determine what role groups are allowed to access given controller and action
  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array("biocore"))

  override def get(id: String) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: FilterSpec) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: Int) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: String) = dispatch(_.update(id))

  override def edit(id: String) = dispatch(_.edit(id))

  override def delete(id: String) = dispatch(_.delete(id))

  override def save = dispatch(_.save)

  override def overviewList(page: Int, orderBy: String, filter: FilterSpec) = dispatch(_.overviewList(page, orderBy, filter))

  override def inferDictionary = dispatch(_.inferDictionary)

  override def dataSetId = ???
}