package persistence.dataset

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import models.DataSetId._
import models.{DataSpaceMetaInfo, DataSetSetting, DataSetMetaInfo}
import persistence.JsObjectCrudRepoFactory
import persistence.RepoTypes._
import play.api.libs.json.{Json, JsObject}
import util.RefreshableCache
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._

import scala.concurrent.Future
import scala.concurrent.Await._

@ImplementedBy(classOf[DataSetAccessorFactoryImpl])
trait DataSetAccessorFactory {
  def register(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    setting: Option[DataSetSetting]
  ): Future[DataSetAccessor]

  def register(
    metaInfo: DataSetMetaInfo,
    setting: Option[DataSetSetting]
  ): Future[DataSetAccessor]
  def apply(dataSetId: String): Option[DataSetAccessor]
}

@Singleton
protected[persistence] class DataSetAccessorFactoryImpl @Inject()(
    dataSetRepoFactory: JsObjectCrudRepoFactory,
    fieldRepoFactory: DictionaryFieldRepoFactory,
    categoryRepoFactory: DictionaryCategoryRepoFactory,
    dataSetMetaInfoRepoFactory: DataSetMetaInfoRepoFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dataSetSettingRepo: DataSetSettingRepo
  ) extends RefreshableCache[String, DataSetAccessor] with DataSetAccessorFactory {

  override def createInstance(dataSetId: String): DataSetAccessor = {
    val collectionName = dataCollectionName(dataSetId)
    val dataSetRepo = dataSetRepoFactory(collectionName)
    val fieldRepo = fieldRepoFactory(dataSetId)
    val categoryRepo = categoryRepoFactory(dataSetId)

    val futureDataSpaceId = dataSpaceMetaInfoRepo.find(
      Some(Json.obj("dataSetMetaInfos.id" -> dataSetId))
    ).map(_.headOption.map(_._id.get))

    val timeout = 120000 millis
    val dataSetMetaInfoRepo = result(futureDataSpaceId, timeout).map(dataSetMetaInfoRepoFactory(_)).getOrElse(
      throw new IllegalArgumentException(s"No data set with id '${dataSetId}' found.")
    )

    new DataSetAccessorImpl(dataSetId, dataSetRepo, fieldRepo, categoryRepo, dataSetMetaInfoRepo, dataSetSettingRepo)
  }

  override def register(
    metaInfo: DataSetMetaInfo,
    setting: Option[DataSetSetting]
  ) = {
    val dataSetMetaInfoRepo = dataSetMetaInfoRepoFactory(metaInfo.dataSpaceId.get)
    dataSetMetaInfoRepo.initIfNeeded
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

  override def register(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    setting: Option[DataSetSetting]
  ) = for {
      // search for data spaces with a given name
      spaces <- dataSpaceMetaInfoRepo.find(Some(Json.obj("name" -> dataSpaceName)))
      // get an id from an existing data space or create a new one
      spaceId <- spaces.headOption.map(space => Future(space._id.get)).getOrElse(
        dataSpaceMetaInfoRepo.save(DataSpaceMetaInfo(None, dataSpaceName))
      )
      // register data set meta info and setting, and obtain an accessor
      accessor <- {
        val metaInfo = DataSetMetaInfo(None, dataSetId, dataSetName, Some(spaceId))
        register(metaInfo, setting)
      }
    } yield
      accessor

  override protected def getAllIds =
    dataSpaceMetaInfoRepo.find().map(
      _.map(_.dataSetMetaInfos.map(_.id)).flatten
    )

  private def dataCollectionName(dataSetId: String) = "data-" + dataSetId
}