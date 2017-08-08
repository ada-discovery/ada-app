package persistence.dataset

import javax.inject.{Inject, Named, Singleton}

import dataaccess._
import models.DataSetFormattersAndIds.DataSetMetaInfoIdentity
import models._
import dataaccess.RepoTypes._
import Criterion.Infix
import play.api.Logger
import play.api.libs.json.JsObject
import util.RefreshableCache
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Await._

trait DataSetAccessorFactory {
  def register(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    setting: Option[DataSetSetting],
    dataView: Option[DataView]
  ): Future[DataSetAccessor]

  def register(
    metaInfo: DataSetMetaInfo,
    setting: Option[DataSetSetting],
    dataView: Option[DataView]
  ): Future[DataSetAccessor]

  def apply(dataSetId: String): Option[DataSetAccessor]
}

@Singleton
protected[persistence] class DataSetAccessorFactoryImpl @Inject()(
    @Named("MongoJsonCrudRepoFactory") mongoDataSetRepoFactory: MongoJsonCrudRepoFactory,
    @Named("ElasticJsonCrudRepoFactory") elasticDataSetRepoFactory: JsonCrudRepoFactory,
    @Named("CachedJsonCrudRepoFactory") cachedDataSetRepoFactory: MongoJsonCrudRepoFactory,
    fieldRepoFactory: FieldRepoFactory,
    categoryRepoFactory: CategoryRepoFactory,
    filterRepoFactory: FilterRepoFactory,
    dataViewRepoFactory: DataViewRepoFactory,
    dataSetMetaInfoRepoFactory: DataSetMetaInfoRepoFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dataSetSettingRepo: DataSetSettingRepo
  ) extends RefreshableCache[String, DataSetAccessor](false) with DataSetAccessorFactory {

  println("Creating DataSetAccessorFactoryImpl!!!")

  override protected def createInstance(
    dataSetId: String
  ): Future[Option[DataSetAccessor]] =
    for {
      dataSpaceId <-
      // TODO: dataSpaceMetaInfoRepo is cached and so querying nested objects "dataSetMetaInfos.id" does not work properly
      //        dataSpaceMetaInfoRepo.find(
      //          Seq("dataSetMetaInfos.id" #== dataSetId)
      //        ).map(_.headOption.map(_._id.get))
      dataSpaceMetaInfoRepo.find().map ( dataSpaceMetaInfos =>
        dataSpaceMetaInfos.find(_.dataSetMetaInfos.map(_.id).contains(dataSetId)).map(_._id.get)
      )
    } yield
      dataSpaceId.map( spaceId =>
        createInstanceAux(dataSetId, spaceId)
      )

  override protected def createInstances(
    dataSetIds: Traversable[String]
  ): Future[Traversable[(String, DataSetAccessor)]] =
    for {
      dataSetSpaceIds <-
        dataSpaceMetaInfoRepo.find().map( dataSpaceMetaInfos =>
          dataSetIds.map( dataSetId =>
            dataSpaceMetaInfos.find(_.dataSetMetaInfos.map(_.id).contains(dataSetId)).map( dataSpace =>
              (dataSetId, dataSpace._id.get)
            )
          ).flatten
        )
    } yield
      dataSetSpaceIds.map { case (dataSetId, spaceId) =>
        val accessor = createInstanceAux(dataSetId, spaceId)
        (dataSetId, accessor)
      }

  private def createInstanceAux(
    dataSetId: String,
    dataSpaceId: BSONObjectID
  ): DataSetAccessor = {
    val fieldRepo = fieldRepoFactory(dataSetId)
    val categoryRepo = categoryRepoFactory(dataSetId)
    val filterRepo = filterRepoFactory(dataSetId)
    val dataViewRepo = dataViewRepoFactory(dataSetId)
    val collectionName = dataCollectionName(dataSetId)

    val dataSetMetaInfoRepo = dataSetMetaInfoRepoFactory(dataSpaceId)

    new DataSetAccessorImpl(
      dataSetId,
      fieldRepo,
      categoryRepo,
      filterRepo,
      dataViewRepo,
      dataSetRepoCreate(collectionName, dataSetId),
      dataSetMetaInfoRepo,
      dataSetSettingRepo
    )
  }

  protected def dataSetRepoCreate(
    collectionName: String,
    dataSetId: String)(
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],
    dataSetSetting: Option[DataSetSetting] = None
  ): Future[JsonCrudRepo] = {
    for {
      dataSetSetting <-
        dataSetSetting match {
          case Some(dataSetSetting) => Future(Some(dataSetSetting))
          case None => dataSetSettingRepo.find(Seq("dataSetId" #== dataSetId)).map(_.headOption)
        }
    } yield {
      val cacheDataSet =
        dataSetSetting.map(_.cacheDataSet).getOrElse(false)

      val storageType =
        dataSetSetting.map(_.storageType).getOrElse(StorageType.Mongo)

      val mongoAutoCreateIndex =
        dataSetSetting.map(_.mongoAutoCreateIndexForProjection).getOrElse(false)

      if (cacheDataSet) {
        println(s"Creating cached data set repo for '$dataSetId'.")
        cachedDataSetRepoFactory(collectionName, fieldNamesAndTypes, mongoAutoCreateIndex)
      } else
        storageType match {
          case StorageType.Mongo => {
            println(s"Creating Mongo based data set repo for '$dataSetId'.")
            mongoDataSetRepoFactory(collectionName, fieldNamesAndTypes, mongoAutoCreateIndex)
          }
          case StorageType.ElasticSearch => {
            println(s"Creating Elastic Search based data set repo for '$dataSetId'.")
            elasticDataSetRepoFactory(collectionName, fieldNamesAndTypes)
          }
        }
      }
    }

  override def register(
    metaInfo: DataSetMetaInfo,
    setting: Option[DataSetSetting],
    dataView: Option[DataView]
  ) = {
    val dataSetMetaInfoRepo = dataSetMetaInfoRepoFactory(metaInfo.dataSpaceId)
    val metaInfosFuture = dataSetMetaInfoRepo.find(Seq("id" #== metaInfo.id))
    val settingsFuture = dataSetSettingRepo.find(Seq("dataSetId" #== metaInfo.id))

    metaInfosFuture.zip(settingsFuture).flatMap { case (metaInfos, settings) =>

      // register setting
      val settingFuture = setting.map( setting =>
        if (settings.isEmpty)
          dataSetSettingRepo.save(setting)
        else
          Future(())
          // dataSetSettingRepo.update(setting.copy(_id = settings.head._id))
      ).getOrElse(
        // if no setting provided either create a dummy one if needed or do nothing
        if (metaInfos.isEmpty)
          dataSetSettingRepo.save(new DataSetSetting(metaInfo.id))
        else
          Future(())
      )

      // register meta info
      val metaInfoFuture =
        if (metaInfos.isEmpty) {
          for {
            dataSpaceMetaInfo <- dataSpaceMetaInfoRepo.get(metaInfo.dataSpaceId)

            metaInfoId <- dataSetMetaInfoRepo.save(metaInfo)

            _ <-
              dataSpaceMetaInfo match {
                case Some(dataSpaceMetaInfo) =>
                  val metaInfoWithId = DataSetMetaInfoIdentity.set(metaInfo, metaInfoId)
                  dataSpaceMetaInfoRepo.update(
                    dataSpaceMetaInfo.copy(dataSetMetaInfos = (dataSpaceMetaInfo.dataSetMetaInfos ++ Seq(metaInfoWithId)))
                  )
                case None => Future(())
              }
          } yield
            metaInfoId
        } else
          // if already exists update the name
          dataSetMetaInfoRepo.update(metaInfos.head.copy(name = metaInfo.name))

      for {
        // execute the setting registration
        _ <- settingFuture

        // execute the meta info registration
        _ <- metaInfoFuture

        // create a data set accessor (and data view repo)
        dsa <- cache.get(metaInfo.id).map(
          Future(_)
        ).getOrElse(
          createInstance(metaInfo.id).map { case Some(dsa) =>
            cache.update(metaInfo.id, dsa)
            dsa
          }
        )

        dataViewRepo = dsa.dataViewRepo

        // check if the data view exist
        dataViewExist <- dataViewRepo.count().map(_ > 0)

        // register (save) data view if none view already exists
        _ <- if (!dataViewExist && dataView.isDefined)
            dsa.dataViewRepo.save(dataView.get)
          else
            Future(())
      } yield
        dsa
    }
  }

  override def register(
    dataSpaceName: String,
    dataSetId: String,
    dataSetName: String,
    setting: Option[DataSetSetting],
    dataView: Option[DataView]
  ) = for {
      // search for data spaces with a given name
      spaces <- dataSpaceMetaInfoRepo.find(Seq("name" #== dataSpaceName))
      // get an id from an existing data space or create a new one
      spaceId <- spaces.headOption.map(space => Future(space._id.get)).getOrElse(
        dataSpaceMetaInfoRepo.save(DataSpaceMetaInfo(None, dataSpaceName, 0))
      )
      // register data set meta info and setting, and obtain an accessor
      accessor <- {
        val metaInfo = DataSetMetaInfo(None, dataSetId, dataSetName, 0, false, spaceId)
        register(metaInfo, setting, dataView)
      }
    } yield
      accessor

  override protected def getAllIds =
    dataSpaceMetaInfoRepo.find().map(
      _.map(_.dataSetMetaInfos.map(_.id)).flatten
    )

  private def dataCollectionName(dataSetId: String) = "data-" + dataSetId
}