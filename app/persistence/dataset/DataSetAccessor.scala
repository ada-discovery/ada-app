package persistence.dataset

import dataaccess.{JsonCrudRepoFactory, Criterion}
import models.{FieldTypeSpec, DataSetSetting, DataSetMetaInfo}
import Criterion.Infix
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import scala.concurrent.Future
import scala.concurrent.Await.result
import dataaccess.RepoTypes._

trait DataSetAccessor {
  def dataSetId: String
  def fieldRepo: FieldRepo
  def categoryRepo: CategoryRepo
  def filterRepo: FilterRepo
  def dataViewRepo: DataViewRepo
  def classificationResultRepo: ClassificationResultRepo

  // following attributes are dynamically created, i.e., each time the respective function is called

  def dataSetRepo: JsonCrudRepo
  def metaInfo: Future[DataSetMetaInfo]
  def dataSetName = metaInfo.map(_.name)
  def setting: Future[DataSetSetting]

  // functions to refresh a few attributes

  def updateDataSetRepo: Future[Unit]
  def updateDataSetRepo(setting: DataSetSetting): Future[Unit]
  def updateSetting(setting: DataSetSetting): Future[BSONObjectID]
  def updateMetaInfo(metaInfo: DataSetMetaInfo): Future[BSONObjectID]
}

protected class DataSetAccessorImpl(
    val dataSetId: String,
    val fieldRepo: FieldRepo,
    val categoryRepo: CategoryRepo,
    val filterRepo: FilterRepo,
    val dataViewRepo: DataViewRepo,
    val classificationResultRepo: ClassificationResultRepo,
    dataSetRepoCreate: (Seq[(String, FieldTypeSpec)], Option[DataSetSetting]) => Future[JsonCrudRepo],
    dataSetMetaInfoRepo: DataSetMetaInfoRepo,
    dataSetSettingRepo: DataSetSettingRepo
  ) extends DataSetAccessor {

  private var _dataSetRepo: Option[JsonCrudRepo] = None

  override def dataSetRepo = {
    if (_dataSetRepo.isEmpty) {
      _dataSetRepo = Some(result(createDataSetRepo(None), 10 seconds))
    }
    _dataSetRepo.get
  }

  private def createDataSetRepo(dataSetSetting: Option[DataSetSetting]) =
    for {
      fields <- fieldRepo.find()
      dataSetRepo <- {
        val fieldNamesAndTypes = fields.map(field => (field.name, field.fieldTypeSpec)).toSeq
        dataSetRepoCreate(fieldNamesAndTypes, dataSetSetting)
      }
    } yield
      dataSetRepo

  override def updateDataSetRepo(setting: DataSetSetting) =
    for {
      newDataSetRepo <- createDataSetRepo(Some(setting))
    } yield
      _dataSetRepo = Some(newDataSetRepo)

  override def updateDataSetRepo =
    for {
      newDataSetRepo <- createDataSetRepo(None)
    } yield
      _dataSetRepo = Some(newDataSetRepo)

  override def metaInfo =
    for {
      metaInfos <- dataSetMetaInfoRepo.find(Seq("id" #== dataSetId))
    } yield
      metaInfos.headOption.getOrElse(
        throw new IllegalStateException("Meta info not available for data set '" + dataSetId + "'.")
      )

  override def setting =
    for {
      settings <- dataSetSettingRepo.find(Seq("dataSetId" #== dataSetId))
    } yield
      settings.headOption.getOrElse(
        throw new IllegalStateException("Setting not available for data set '" + dataSetId + "'.")
      )

  override def updateSetting(setting: DataSetSetting) =
    dataSetSettingRepo.update(setting)

  override def updateMetaInfo(metaInfo: DataSetMetaInfo) =
    dataSetMetaInfoRepo.update(metaInfo)
}