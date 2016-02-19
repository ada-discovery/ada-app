package controllers

import play.api.mvc.{Controller}
import reactivemongo.bson.BSONObjectID
import util.FilterSpec

class DataSetDispatcher extends Controller {

  def get(controllerId : String)(id: BSONObjectID) = ???

  def find(controllerId : String)(page: Int, orderBy: String, filter: FilterSpec) = ???

  def listAll(controllerId : String)(orderBy: Int) = ???

  def exportAllRecordsAsCsv(controllerId : String)(delimiter : String) = ???

  def exportAllRecordsAsJson(controllerId : String) = ???

  def exportRecordsAsCsv(controllerId : String)(delimiter : String, filter: FilterSpec) = ???

  def exportRecordsAsJson(filter: FilterSpec) = ???

  def overviewFieldTypes(controllerId : String) = ???

  def overview(controllerId : String) = ???

  def overviewList(controllerId : String)(page: Int, orderBy: String, filter: FilterSpec) = ???

  def getScatterStats(controllerId : String)(xFieldName : String, yFieldName : String) = ???

  def getDistribution(controllerId : String)(fieldName: String) = ???

  def exportTranSMARTDataFile(controllerId : String)(delimiter : String) = ???

  def exportTranSMARTMappingFile(controllerId : String)(delimiter : String) = ???
}