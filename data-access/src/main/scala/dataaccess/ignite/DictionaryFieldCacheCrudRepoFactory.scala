package dataaccess.ignite

import javax.inject.Inject

import dataaccess.DataSetFormattersAndIds.FieldIdentity
import dataaccess.RepoTypes.DictionaryFieldRepo
import dataaccess._
import dataaccess.mongo.dataset.DictionaryFieldMongoAsyncCrudRepoFactory
import play.api.Configuration

protected[dataaccess] class DictionaryFieldCacheCrudRepoFactory @Inject()(
    cacheRepoFactory: CacheAsyncCrudRepoFactory,
    configuration: Configuration
  ) extends DictionaryFieldRepoFactory {

  def apply(dataSetId: String): DictionaryFieldRepo = {
    val cacheName = "Field_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new DictionaryFieldMongoAsyncCrudRepoFactory(dataSetId, configuration, new SerializableApplicationLifecycle())
    cacheRepoFactory(mongoRepoFactory, cacheName)
  }
}