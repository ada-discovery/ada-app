package persistence.dataset

import javax.inject.{Named, Inject}

import dataaccess._
import models._
import dataaccess.RepoTypes.DataSetSettingRepo
import Criterion.Infix
import persistence.RepoTypes._
import play.api.libs.json.JsObject
import util.RefreshableCache
import play.api.libs.concurrent.Execution.Implicits.defaultContext
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

protected[persistence] class DataSetAccessorFactoryImpl @Inject()(
    @Named("MongoJsonCrudRepoFactory") mongoDataSetRepoFactory: JsonCrudRepoFactory,
    @Named("ElasticJsonCrudRepoFactory") elasticDataSetRepoFactory: JsonCrudRepoFactory,
    @Named("CachedJsonCrudRepoFactory") cachedDataSetRepoFactory: JsonCrudRepoFactory,
    fieldRepoFactory: FieldRepoFactory,
    categoryRepoFactory: CategoryRepoFactory,
    filterRepoFactory: FilterRepoFactory,
    dataViewRepoFactory: DataViewRepoFactory,
    dataSetMetaInfoRepoFactory: DataSetMetaInfoRepoFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dataSetSettingRepo: DataSetSettingRepo
  ) extends RefreshableCache[String, DataSetAccessor] with DataSetAccessorFactory {

  override protected def createInstance(dataSetId: String): DataSetAccessor = {
    val fieldRepo = fieldRepoFactory(dataSetId)
    val categoryRepo = categoryRepoFactory(dataSetId)
    val filterRepo = filterRepoFactory(dataSetId)
    val dataViewRepo = dataViewRepoFactory(dataSetId)

    val collectionName = dataCollectionName(dataSetId)

    val dataSetAccessorFuture = for {
      cacheDataSet <-
        dataSetSettingRepo.find(Seq("dataSetId" #== dataSetId)).map(
          _.headOption.map(_.cacheDataSet).getOrElse(false))

      fieldNamesAndTypes <-
        fieldRepo.find().map(_.map(field =>
          (field.name, field.fieldTypeSpec)
        ).toSeq)

      dataSpaceId <-
        dataSpaceMetaInfoRepo.find(
          Seq("dataSetMetaInfos.id" #== dataSetId)
        ).map(_.headOption.map(_._id.get))

    } yield {
      val dataSetRepo =
        if (cacheDataSet)
          cachedDataSetRepoFactory(collectionName, fieldNamesAndTypes)
        else
          elasticDataSetRepoFactory(collectionName, fieldNamesAndTypes)

      val dataSetMetaInfoRepo =
        dataSpaceId.map(dataSetMetaInfoRepoFactory(_)).getOrElse(
          throw new IllegalArgumentException(s"No data set with id '${dataSetId}' found.")
        )

      new DataSetAccessorImpl(
        dataSetId,
        dataSetRepo,
        fieldRepo,
        categoryRepo,
        filterRepo,
        dataViewRepo,
        dataSetMetaInfoRepo,
        dataSetSettingRepo
      )
    }

    result(dataSetAccessorFuture, 2 minutes)
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
      val metaInfoFuture = if (metaInfos.isEmpty)
        dataSetMetaInfoRepo.save(metaInfo)
      else
        // if already exists update the name
        dataSetMetaInfoRepo.update(metaInfos.head.copy(name = metaInfo.name))

      for {
        // execute the setting registration
        _ <- settingFuture

        // execute the meta info registration
        _ <- metaInfoFuture

        // create a data set accessor (and data view repo)
        dsa = createInstance(metaInfo.id)
        dataViewRepo = dsa.dataViewRepo

        // check if the data view exist
        dataViewExist <- dataViewRepo.count().map(_ > 0)

        // register (save) data view if none view already exists
        _ <- if (!dataViewExist && dataView.isDefined)
            dsa.dataViewRepo.save(dataView.get)
          else
            Future(())
      } yield
        cache.getOrElseUpdate(metaInfo.id, dsa)
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