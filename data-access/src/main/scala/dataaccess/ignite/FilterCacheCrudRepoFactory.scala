package dataaccess.ignite

import javax.inject.Inject

import dataaccess.RepoTypes.FilterRepo
import dataaccess._
import dataaccess.mongo.dataset.FilterMongoAsyncCrudRepoFactory
import models.FilterCondition.FilterIdentity
import play.api.Configuration

protected[dataaccess] class FilterCacheCrudRepoFactory @Inject()(
    cacheRepoFactory: CacheAsyncCrudRepoFactory,
    configuration: Configuration
  ) extends FilterRepoFactory {

  def apply(dataSetId: String): FilterRepo = {
    val cacheName = "Filter_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new FilterMongoAsyncCrudRepoFactory(dataSetId, configuration, new SerializableApplicationLifecycle())
    cacheRepoFactory(mongoRepoFactory, cacheName)
  }
}