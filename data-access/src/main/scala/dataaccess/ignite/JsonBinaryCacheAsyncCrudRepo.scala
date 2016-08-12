package dataaccess.ignite

import javax.inject.Inject

import dataaccess.DataSetFormattersAndIds.JsObjectIdentity
import dataaccess._
import dataaccess.RepoTypes._
import dataaccess.ignite.BinaryJsonUtil._
import dataaccess.mongo.JsonRepoFactory
import org.apache.ignite.{Ignite, IgniteCache}
import org.apache.ignite.binary.BinaryObject
import play.api.Configuration
import play.api.libs.json.JsObject

class JsonBinaryCacheAsyncCrudRepo[ID](
    cache: IgniteCache[ID, BinaryObject],
    cacheName: String,
    ignite: Ignite,
    identity: Identity[JsObject, ID]
  ) extends AbstractCacheAsyncCrudRepo[ID, JsObject, ID, BinaryObject](cache, cacheName, identity) {

  private val igniteBinary = ignite.binary
  private val toBinary = toBinaryObject(igniteBinary, fieldNameClassMap, cacheName)_

  // hooks
  override def toCacheId(id: ID) =
    id

  override def toItem(cacheItem: BinaryObject) =
    toJsObject(cacheItem)

  override def toCacheItem(item: JsObject) =
    toBinary(item)

  override def queryResultToItem(result: Seq[(String, Any)]) =
    toJsObject(result)
}

// protected[dataaccess]
class JsonBinaryCacheAsyncCrudRepoFactory @Inject()(
    ignite: Ignite,
    dictionaryFieldRepoFactory: DictionaryFieldRepoFactory,
    cacheFactory: BinaryCacheFactory,
    configuration: Configuration
  ) extends JsonCrudRepoFactory {

  override def apply(collectionName: String): JsonCrudRepo =
    applyWithDictionaryAux(collectionName, Nil)

  override def applyWithDictionary(collectionName: String, fieldNamesAndTypes: Seq[(String, FieldType.Value)]) =
    applyWithDictionaryAux(collectionName, fieldNamesAndTypes)

  private def applyWithDictionaryAux(
    collectionName: String,
    fieldNamesAndTypes: Seq[(String, FieldType.Value)]
  ): JsonCrudRepo = {
    val cacheName = collectionName.replaceAll("[\\.-]", "_")
    val identity = JsObjectIdentity

    val cache = cacheFactory(
      new JsonRepoFactory(collectionName, configuration, new SerializableApplicationLifecycle()),
      identity.of,
      identity.name,
      cacheName,
      fieldNamesAndTypes
    ) // new DefaultApplicationLifecycle().addStopHook
    cache.loadCache(null)
    new JsonBinaryCacheAsyncCrudRepo(cache, cacheName, ignite, identity)
  }
}