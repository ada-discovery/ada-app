package controllers

import reactivemongo.bson.BSONObjectID
import util.FilterSpec

class DataSetDispatcher(controllers : Iterable[(String, DataSetController)]) extends ControllerDispatcher[DataSetController]("dataSet", controllers) with DataSetController {

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

  override def getScatterStats(xFieldName : String, yFieldName : String) = dispatch(_.getScatterStats(xFieldName, yFieldName))

  override def getDistribution(fieldName: String) = dispatch(_.getDistribution(fieldName))

  override def exportTranSMARTDataFile(delimiter : String) = dispatch(_.exportTranSMARTDataFile(delimiter))

  override def exportTranSMARTMappingFile(delimiter : String) = dispatch(_.exportTranSMARTMappingFile(delimiter))

  override def dataSetName = ???
}