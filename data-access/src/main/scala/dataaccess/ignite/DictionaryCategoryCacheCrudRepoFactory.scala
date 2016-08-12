package dataaccess.ignite

import javax.inject.Inject

import dataaccess.DataSetFormattersAndIds.CategoryIdentity
import dataaccess.RepoTypes.DictionaryCategoryRepo
import dataaccess._
import dataaccess.mongo.dataset.DictionaryCategoryMongoAsyncCrudRepoFactory
import play.api.Configuration

protected[dataaccess] class DictionaryCategoryCacheCrudRepoFactory @Inject()(
    cacheRepoFactory: CacheAsyncCrudRepoFactory,
    configuration: Configuration
  ) extends DictionaryCategoryRepoFactory {

  def apply(dataSetId: String): DictionaryCategoryRepo = {
    val cacheName = "Category_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new DictionaryCategoryMongoAsyncCrudRepoFactory(dataSetId, configuration, new SerializableApplicationLifecycle())
    cacheRepoFactory(mongoRepoFactory, cacheName)
  }
}