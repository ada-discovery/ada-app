package persistence.dataset

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import models.{DataSetSetting, DataSetMetaInfo}
import persistence.JsObjectCrudRepoFactory
import persistence.RepoTypes._
import play.api.libs.json.{Json, JsObject}
import util.RefreshableCache
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

@ImplementedBy(classOf[DataSetAccessorFactoryImpl])
trait DataSetAccessorFactory {
  def register(metaInfo: DataSetMetaInfo, setting: Option[DataSetSetting] = None): Future[DataSetAccessor]
  def apply(dataSetId: String): Option[DataSetAccessor]
}

@Singleton
protected[persistence] class DataSetAccessorFactoryImpl @Inject()(
    dataSetRepoFactory: JsObjectCrudRepoFactory,
    fieldRepoFactory: DictionaryFieldRepoFactory,
    categoryRepoFactory: DictionaryCategoryRepoFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo,
    dataSetSettingRepo: DataSetSettingRepo
  ) extends RefreshableCache[String, DataSetAccessor] with DataSetAccessorFactory {

  override def createInstance(dataSetId: String): DataSetAccessor = {
    val collectionName = dataCollectionName(dataSetId)
    val dataSetRepo = dataSetRepoFactory(collectionName)
    val fieldRepo = fieldRepoFactory(dataSetId)
    val categoryRepo = categoryRepoFactory(dataSetId)

    new DataSetAccessorImpl(dataSetId, dataSetRepo, fieldRepo, categoryRepo, dataSetMetaInfoRepo, dataSetSettingRepo)
  }

  override def register(metaInfo: DataSetMetaInfo, setting: Option[DataSetSetting]) = {
    val metaInfosFuture = dataSetMetaInfoRepo.find(Some(Json.obj("id" -> metaInfo.id)))
    val settingsFuture = dataSetSettingRepo.find(Some(Json.obj("dataSetId" -> metaInfo.id)))

    metaInfosFuture.zip(settingsFuture).flatMap { case (metaInfos, settings) =>
      val futureId = if (metaInfos.isEmpty)

        // register meta info and setting
        for {
          _ <- dataSetSettingRepo.save(setting.getOrElse(new DataSetSetting(metaInfo.id)))
          id <- dataSetMetaInfoRepo.save(metaInfo)
        } yield id

      else {
        // otherwise update

        val settingUpdateFuture = setting.map( setting =>
          dataSetSettingRepo.update(setting.copy(_id = settings.head._id))
        ).getOrElse(
          // if no setting provided do nothing
          Future(())
        )

        for {
          _ <- settingUpdateFuture
          id <- dataSetMetaInfoRepo.update(metaInfos.head.copy(name = metaInfo.name))
        } yield id
      }

      futureId.map { _ =>
        cache.getOrElseUpdate(metaInfo.id, createInstance(metaInfo.id))
      }
    }
  }

  override protected def getAllIds =
    dataSetMetaInfoRepo.find().map(_.map(_.id))

  private def dataCollectionName(dataSetId: String) = "data-" + dataSetId
}
