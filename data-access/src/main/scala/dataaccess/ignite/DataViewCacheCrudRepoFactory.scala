package dataaccess.ignite

import javax.inject.Inject

import dataaccess.RepoTypes.DataViewRepo
import dataaccess._
import dataaccess.mongo.dataset.DataViewMongoAsyncCrudRepoFactory
import models.DataView.DataViewIdentity
import play.api.Configuration

protected[dataaccess] class DataViewCacheCrudRepoFactory @Inject()(
    cacheRepoFactory: CacheAsyncCrudRepoFactory,
    configuration: Configuration
  ) extends DataViewRepoFactory {

  def apply(dataSetId: String): DataViewRepo = {
    val cacheName = "DataView_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new DataViewMongoAsyncCrudRepoFactory(dataSetId, configuration, new SerializableApplicationLifecycle())
    cacheRepoFactory(mongoRepoFactory, cacheName)
  }
}