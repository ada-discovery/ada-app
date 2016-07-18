package controllers.dataset

import javax.inject.Inject

import controllers.{SecureControllerDispatcher, ControllerDispatcher}
import play.api.mvc.{AnyContent, Action}
import reactivemongo.bson.BSONObjectID
import util.FilterSpec
import util.SecurityUtil._

class DataSetDispatcher @Inject() (dscf: DataSetControllerFactory) extends SecureControllerDispatcher[DataSetController]("dataSet") with DataSetController {

  override protected def getController(id: String) =
    dscf(id).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override protected def getAllowedRoleGroups(
    controllerId: String,
    actionName: String
  ) = List(Array("admin"))

  override protected def getPermission(
    controllerId: String,
    actionName: String
  ) = Some(createDataSetPermission(controllerId, "dataSet", actionName))

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: FilterSpec) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def exportAllRecordsAsCsv(delimiter : String) = dispatch(_.exportAllRecordsAsCsv(delimiter))

  override def exportAllRecordsAsJson = dispatch(_.exportAllRecordsAsJson())

  override def exportRecordsAsCsv(delimiter : String, filter: FilterSpec) = dispatch(_.exportRecordsAsCsv(delimiter, filter))

  override def exportRecordsAsJson(filter: FilterSpec) = dispatch(_.exportRecordsAsJson(filter))

  override def overviewFieldTypes = dispatch(_.overviewFieldTypes())

  override def overviewList(page: Int, orderBy: String, filter: FilterSpec) = dispatch(_.overviewList(page, orderBy, filter))

  override def getScatterStats(xFieldName: Option[String], yFieldName: Option[String], filter: FilterSpec) = dispatch(_.getScatterStats(xFieldName, yFieldName, filter))

  override def getDistribution(fieldName: Option[String], filter: FilterSpec) = dispatch(_.getDistribution(fieldName, filter))

  override def exportTranSMARTDataFile(delimiter : String) = dispatch(_.exportTranSMARTDataFile(delimiter))

  override def exportTranSMARTMappingFile(delimiter : String) = dispatch(_.exportTranSMARTMappingFile(delimiter))

  override def getFieldNames = dispatch(_.getFieldNames)

  override def getFieldValue(id: BSONObjectID, fieldName: String) = dispatch(_.getFieldValue(id, fieldName))

  override def jsRoutes: Action[AnyContent] = dispatch(_.jsRoutes)
}