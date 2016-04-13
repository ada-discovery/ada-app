package persistence.dataset

import models.{DataSetSetting, DataSetMetaInfo}
import play.api.libs.json._
import persistence.RepoTypes._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import scala.concurrent.Future

trait DataSetAccessor{
  def dataSetId: String
  def dataSetRepo: JsObjectCrudRepo
  def fieldRepo: DictionaryFieldRepo
  def categoryRepo: DictionaryCategoryRepo
  def metaInfo: Future[DataSetMetaInfo]
  def setting: Future[DataSetSetting]
  def updateSetting(setting: DataSetSetting): Future[BSONObjectID]
}

protected class DataSetAccessorImpl(
  val dataSetId: String,
  val dataSetRepo: JsObjectCrudRepo,
  val fieldRepo: DictionaryFieldRepo,
  val categoryRepo: DictionaryCategoryRepo,
  dataSetMetaInfoRepo: DataSetMetaInfoRepo,
  dataSetSettingRepo: DataSetSettingRepo
  ) extends DataSetAccessor{

  override def metaInfo = {
    val metaInfosFuture = dataSetMetaInfoRepo.find(Some(Json.obj("id" -> dataSetId)))
    metaInfosFuture.map {
      _.headOption.getOrElse(
        throw new IllegalAccessError("Meta info not available for data set '" + dataSetId + "'.")
      )
    }
  }

  override def setting = {
    val settingsFuture = dataSetSettingRepo.find(Some(Json.obj("dataSetId" -> dataSetId)))
    settingsFuture.map {
      _.headOption.getOrElse {
        throw new IllegalAccessError("Setting not available for data set '" + dataSetId + "'.")
      }
    }
  }

  override def updateSetting(setting: DataSetSetting) =
    dataSetSettingRepo.update(setting)
}