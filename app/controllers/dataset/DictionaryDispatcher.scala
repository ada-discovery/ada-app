package controllers.dataset

import controllers.SecureControllerDispatcher
import models.FilterCondition
import javax.inject.Inject

import util.SecurityUtil._

class DictionaryDispatcher @Inject() (dscf: DataSetControllerFactory, dcf: DictionaryControllerFactory)
  extends SecureControllerDispatcher[DictionaryController]("dataSet") with DictionaryController {

  override protected def getController(id: String) =
    dscf(id).map(_ => dcf(id)).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array("admin"))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(createDataSetPermission(controllerId, "field", actionName))

  override def get(id: String) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: Seq[FilterCondition]) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: String) = dispatch(_.update(id))

  override def edit(id: String) = dispatch(_.edit(id))

  override def delete(id: String) = dispatch(_.delete(id))

  override def save = dispatch(_.save)

  override def updateLabel(id: String, label: String) = dispatchAjax(_.updateLabel(id, label))

  override def jsRoutes = dispatch(_.jsRoutes)

  override def exportRecordsAsCsv(
    delimiter : String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsCsv(delimiter, replaceEolWithSpace, eol, filter, tableColumnsOnly))

  override def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsJson(filter, tableColumnsOnly))
}