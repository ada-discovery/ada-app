package dataaccess.ignite

import javax.inject.Inject

import dataaccess.RepoTypes.DataSetMetaInfoRepo
import dataaccess._
import dataaccess.mongo.dataset.DataSetMetaInfoMongoAsyncCrudRepoFactory
import play.api.Configuration
import reactivemongo.bson.BSONObjectID
import models.DataSetFormattersAndIds.DataSetMetaInfoIdentity

class DataSetMetaInfoCacheCrudRepoFactory @Inject()(
    cacheRepoFactory: CacheAsyncCrudRepoFactory,
    configuration: Configuration
  ) extends DataSetMetaInfoRepoFactory {

  def apply(dataSpaceId: BSONObjectID): DataSetMetaInfoRepo = {
    val cacheName = "DataSetMetaInfo_" + dataSpaceId.stringify
    val mongoRepoFactory = new DataSetMetaInfoMongoAsyncCrudRepoFactory(dataSpaceId, configuration, new SerializableApplicationLifecycle())
    cacheRepoFactory(mongoRepoFactory, cacheName)
  }
}