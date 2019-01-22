package dataaccess.ignite

import javax.inject.Inject

import models.DataSetFormattersAndIds
import DataSetFormattersAndIds.FieldIdentity
import dataaccess.RepoTypes.FieldRepo
import dataaccess._
import dataaccess.mongo.dataset.FieldMongoAsyncCrudRepoFactory
import play.api.Configuration

protected[dataaccess] class FieldCacheCrudRepoFactory @Inject()(
    cacheRepoFactory: CacheAsyncCrudRepoFactory,
    configuration: Configuration
  ) extends FieldRepoFactory {

  def apply(dataSetId: String): FieldRepo = {
    val cacheName = "Field_" + dataSetId.replaceAll("[\\.-]", "_")
    val mongoRepoFactory = new FieldMongoAsyncCrudRepoFactory(dataSetId, configuration, new SerializableApplicationLifecycle())
    cacheRepoFactory(mongoRepoFactory, cacheName, Set("numValues"))
  }
}