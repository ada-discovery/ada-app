package persistence.dataset

import dataaccess.Criterion
import models.{DataSetSetting, DataSetMetaInfo}
import dataaccess.RepoTypes.JsonCrudRepo
import Criterion.CriterionInfix
import play.api.libs.json._
import persistence.RepoTypes._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import scala.concurrent.Future
import dataaccess.RepoTypes._

trait DataSetAccessor{
  def dataSetId: String
  def dataSetRepo: JsonCrudRepo
  def fieldRepo: FieldRepo
  def categoryRepo: CategoryRepo
  def filterRepo: FilterRepo
  def dataViewRepo: DataViewRepo
  def metaInfo: Future[DataSetMetaInfo]
  def setting: Future[DataSetSetting]
  def updateSetting(setting: DataSetSetting): Future[BSONObjectID]
}

protected class DataSetAccessorImpl(
  val dataSetId: String,
  val dataSetRepo: JsonCrudRepo,
  val fieldRepo: FieldRepo,
  val categoryRepo: CategoryRepo,
  val filterRepo: FilterRepo,
  val dataViewRepo: DataViewRepo,
  dataSetMetaInfoRepo: DataSetMetaInfoRepo,
  dataSetSettingRepo: DataSetSettingRepo
  ) extends DataSetAccessor{

  override def metaInfo = {
    val metaInfosFuture = dataSetMetaInfoRepo.find(Seq("id" #== dataSetId))
    metaInfosFuture.map {
      _.headOption.getOrElse(
        throw new IllegalStateException("Meta info not available for data set '" + dataSetId + "'.")
      )
    }
  }

  override def setting = {
    val settingsFuture = dataSetSettingRepo.find(Seq("dataSetId" #== dataSetId))
    settingsFuture.map {
      _.headOption.getOrElse {
        throw new IllegalStateException("Setting not available for data set '" + dataSetId + "'.")
      }
    }
  }

  override def updateSetting(setting: DataSetSetting) =
    dataSetSettingRepo.update(setting)
}