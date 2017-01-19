package dataaccess.mongo.dataset

import javax.cache.configuration.Factory
import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import dataaccess.mongo.{SubordinateObjectMongoAsyncCrudRepo, ReactiveMongoApi, MongoAsyncCrudRepo}
import dataaccess.{Identity, AsyncCrudRepo}
import dataaccess.RepoTypes._
import models.{DataSpaceMetaInfo, DataSetMetaInfo, Dictionary}
import models.DataSetFormattersAndIds.{DataSetMetaInfoIdentity, dataSetMetaInfoFormat, DataSpaceMetaInfoIdentity, dataSpaceMetaInfoFormat}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.Format
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import dataaccess.Criterion.Infix

//class DataSetMetaInfoMongoAsyncCrudRepo @Inject()(
//    @Assisted dataSpaceId: BSONObjectID,
//    dataSpaceMetaInfoRepo: MongoDataSpaceMetaInfoRepo
//  ) extends SubordinateObjectMongoAsyncCrudRepo[DataSetMetaInfo, BSONObjectID, DataSpaceMetaInfo, BSONObjectID]("dataSetMetaInfos", dataSpaceMetaInfoRepo) {
//
//  override protected def getDefaultRoot =
//    DataSpaceMetaInfo(None, "", 0)
//
//  override protected def getRootObject =
//    dataSpaceMetaInfoRepo.get(dataSpaceId)
//
//  override def save(entity: DataSetMetaInfo): Future[BSONObjectID] = {
//    val initializedId = DataSetMetaInfoIdentity.of(entity).getOrElse(BSONObjectID.generate)
//    super.save(DataSetMetaInfoIdentity.set(entity, initializedId))
//  }
//}
//
//class DataSetMetaInfoMongoAsyncCrudRepoFactory(
//    dataSpaceId: BSONObjectID,
//    configuration: Configuration,
//    applicationLifecycle: ApplicationLifecycle
//  ) extends Factory[AsyncCrudRepo[DataSetMetaInfo, BSONObjectID]] {
//
//  override def create(): AsyncCrudRepo[DataSetMetaInfo, BSONObjectID] = {
//    val dataSpaceRepo = new MongoAsyncCrudRepo[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos")
//    dataSpaceRepo.reactiveMongoApi = ReactiveMongoApi.create(configuration, applicationLifecycle)
//
//    val repo = new DataSetMetaInfoMongoAsyncCrudRepo(dataSpaceId, dataSpaceRepo)
//    Await.result(repo.initIfNeeded, 10 seconds)
//    repo
//  }
//}