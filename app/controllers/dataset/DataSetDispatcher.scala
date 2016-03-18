package controllers.dataset

import javax.inject.Inject

import controllers.ControllerDispatcher
import reactivemongo.bson.BSONObjectID
import util.FilterSpec

class DataSetDispatcher @Inject() (dscf: DataSetControllerFactory) extends ControllerDispatcher[DataSetController]("dataSet") with DataSetController {

  override protected def getController(id: String) =
    dscf(id).getOrElse(
      throw new IllegalArgumentException(s"Controller id '${id}' not recognized.")
    )

  override def get(id: BSONObjectID) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: FilterSpec) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: Int) = dispatch(_.listAll(orderBy))

  override def exportAllRecordsAsCsv(delimiter : String) = dispatch(_.exportAllRecordsAsCsv(delimiter))

  override def exportAllRecordsAsJson = dispatch(_.exportAllRecordsAsJson())

  override def exportRecordsAsCsv(delimiter : String, filter: FilterSpec) = dispatch(_.exportRecordsAsCsv(delimiter, filter))

  override def exportRecordsAsJson(filter: FilterSpec) = dispatch(_.exportRecordsAsJson(filter))

  override def overviewFieldTypes = dispatch(_.overviewFieldTypes())

  override def overview = dispatch(_.overview())

  override def overviewList(page: Int, orderBy: String, filter: FilterSpec) = dispatch(_.overviewList(page, orderBy, filter))

  override def getScatterStats(xFieldName: Option[String], yFieldName: Option[String], filter: FilterSpec) = dispatch(_.getScatterStats(xFieldName, yFieldName, filter))

  override def getDistribution(fieldName: Option[String], filter: FilterSpec) = dispatch(_.getDistribution(fieldName, filter))

  override def exportTranSMARTDataFile(delimiter : String) = dispatch(_.exportTranSMARTDataFile(delimiter))

  override def exportTranSMARTMappingFile(delimiter : String) = dispatch(_.exportTranSMARTMappingFile(delimiter))

  override def getFieldNames = dispatch(_.getFieldNames)

  override def dataSetId = ???
}