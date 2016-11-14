package controllers.dataset

import javax.inject.Inject

import controllers.{SecureControllerDispatcher, ControllerDispatcher}
import models.FieldTypeId
import models.FilterCondition
import play.api.mvc.{AnyContent, Action}
import reactivemongo.bson.BSONObjectID
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

  override def find(
    page: Int,
    orderBy: String,
    filter: Seq[FilterCondition]
  ) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: String) = dispatch(_.listAll(orderBy))

  override def overviewFieldTypes = dispatch(_.overviewFieldTypes())

  override def overviewList(
    page: Int,
    orderBy: String,
    filterOrId: Either[Seq[FilterCondition], BSONObjectID]
  ) = dispatch(_.overviewList(page, orderBy, filterOrId))

  override def getScatterStats(
    xFieldName: Option[String],
    yFieldName: Option[String],
    groupFieldName: Option[String],
    filter: Seq[FilterCondition]
  ) = dispatch(_.getScatterStats(xFieldName, yFieldName, groupFieldName, filter))

  override def getDistribution(fieldName: Option[String], filter: Seq[FilterCondition]) = dispatch(_.getDistribution(fieldName, filter))

  override def getDateCount(
    dateFieldName: Option[String],
    groupFieldName: Option[String],
    filter: Seq[FilterCondition]
  ) = dispatch(_.getDateCount(dateFieldName, groupFieldName, filter))

  override def getFields(
    fieldTypeIds: Seq[FieldTypeId.Value]
  ) = dispatch(_.getFields(fieldTypeIds))

  override def getFieldNames = dispatch(_.getFieldNames)

  override def getFieldValue(
    id: BSONObjectID,
    fieldName: String
  ) = dispatch(_.getFieldValue(id, fieldName))

  override def exportRecordsAsCsv(
    delimiter: String,
    replaceEolWithSpace: Boolean,
    eol: Option[String],
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsCsv(delimiter, replaceEolWithSpace, eol, filter, tableColumnsOnly))

  override def exportRecordsAsJson(
    filter: Seq[FilterCondition],
    tableColumnsOnly: Boolean
  ) = dispatch(_.exportRecordsAsJson(filter, tableColumnsOnly))

  override def exportTranSMARTDataFile(delimiter : String) = dispatch(_.exportTranSMARTDataFile(delimiter))

  override def exportTranSMARTMappingFile(delimiter : String) = dispatch(_.exportTranSMARTMappingFile(delimiter))
}