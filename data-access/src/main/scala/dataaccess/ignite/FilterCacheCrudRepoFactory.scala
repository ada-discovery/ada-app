package dataaccess.ignite

import javax.inject.Inject

import models.DataSetFormattersAndIds
import DataSetFormattersAndIds.CategoryIdentity
import dataaccess.RepoTypes.CategoryRepo
import dataaccess._
import dataaccess.mongo.dataset.CategoryMongoAsyncCrudRepoFactory
import play.api.Configuration

protected[dataaccess] class FilterCacheCrudRepoFactory @Inject()(
    cacheRepoFactory: CacheAsyncCrudRepoFactory,
    configuration: Configuration
  ) extends CategoryRepoFactory {

  def apply(dataSetId: String): CategoryRepo = {
    val cacheName = "Category_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new CategoryMongoAsyncCrudRepoFactory(dataSetId, configuration, new SerializableApplicationLifecycle())
    cacheRepoFactory(mongoRepoFactory, cacheName)
  }
}