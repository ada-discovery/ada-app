package dataaccess.mongo.dataset

import javax.cache.configuration.Factory
import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import dataaccess.mongo.{MongoAsyncCrudExtraRepo, MongoAsyncCrudRepo, ReactiveMongoApi, SubordinateObjectMongoAsyncCrudRepo}
import models.DataSetFormattersAndIds._
import models._
import org.incal.core.dataaccess.AsyncCrudRepo
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class DataSetMetaInfoMongoAsyncCrudRepoFactory(
    dataSpaceId: BSONObjectID,
    configuration: Configuration,
    applicationLifecycle: ApplicationLifecycle
  ) extends Factory[AsyncCrudRepo[DataSetMetaInfo, BSONObjectID]] {

  override def create(): AsyncCrudRepo[DataSetMetaInfo, BSONObjectID] = {
    val dataSpaceRepo = new MongoAsyncCrudRepo[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos")
    dataSpaceRepo.reactiveMongoApi = ReactiveMongoApi.create(configuration, applicationLifecycle)

    val repo = new DataSetMetaInfoMongoAsyncCrudRepo(dataSpaceId, dataSpaceRepo)
    Await.result(repo.initIfNeeded, 30 seconds)
    repo
  }
}

class DataSetMetaInfoMongoAsyncCrudRepo @Inject()(
    @Assisted dataSpaceId: BSONObjectID,
    dataSpaceMetaInfoRepo: MongoAsyncCrudExtraRepo[DataSpaceMetaInfo, BSONObjectID]
  ) extends SubordinateObjectMongoAsyncCrudRepo[DataSetMetaInfo, BSONObjectID, DataSpaceMetaInfo, BSONObjectID]("dataSetMetaInfos", dataSpaceMetaInfoRepo) {

  override protected lazy val rootId = dataSpaceId

  override protected def getDefaultRoot =
    DataSpaceMetaInfo(Some(dataSpaceId), "", 0, new java.util.Date(), Seq[DataSetMetaInfo]())

  override protected def getRootObject =
    Future(Some(getDefaultRoot))
  //    rootRepo.find(Seq(DataSpaceMetaInfoIdentity.name #== dataSpaceId)).map(_.headOption)

  override def save(entity: DataSetMetaInfo): Future[BSONObjectID] = {
    val identity = DataSetMetaInfoIdentity
    val initializedId = identity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(identity.set(entity, initializedId))
  }
}