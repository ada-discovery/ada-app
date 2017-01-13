package dataaccess.ignite

import javax.inject.Inject

import models.{FieldTypeSpec, FieldTypeId, DataSetFormattersAndIds}
import DataSetFormattersAndIds.JsObjectIdentity
import dataaccess._
import dataaccess.RepoTypes._
import dataaccess.ignite.BinaryJsonUtil._
import dataaccess.mongo.MongoJsonRepoFactory
import org.apache.ignite.{Ignite, IgniteCache}
import org.apache.ignite.binary.BinaryObject
import play.api.Configuration
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

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

  override def findResultToItem(result: Traversable[(String, Any)]) =
    toJsObject(result)
}

// protected[dataaccess]
class JsonBinaryCacheAsyncCrudRepoFactory @Inject()(
    ignite: Ignite,
    cacheFactory: BinaryCacheFactory,
    configuration: Configuration
  ) extends JsonCrudRepoFactory {

  private val ftf = FieldTypeFactory(Set[String](""), Seq[String](), "", ",")

  override def apply(collectionName: String, fieldNamesAndTypes: Seq[(String, FieldTypeSpec)]) =
    applyWithDictionaryAux(collectionName, fieldNamesAndTypes)

  private def applyWithDictionaryAux(
    collectionName: String,
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)]
  ): JsonCrudRepo = {
    val cacheName = collectionName.replaceAll("[\\.-]", "_")
    val identity = JsObjectIdentity

    val fieldNamesAndClasses: Seq[(String, Class[_ >: Any])] =
      (fieldNamesAndTypes.map{ case (fieldName, fieldTypeSpec) =>
        (escapeIgniteFieldName(fieldName), ftf(fieldTypeSpec).valueClass.asInstanceOf[Class[_ >: Any]])
      } ++ Seq((identity.name, classOf[Option[BSONObjectID]].asInstanceOf[Class[_ >: Any]])))

    val cache = cacheFactory(
      cacheName,
      fieldNamesAndClasses,
      new MongoJsonRepoFactory(collectionName, configuration, new SerializableApplicationLifecycle()),
      identity.of(_)
    ) // new DefaultApplicationLifecycle().addStopHook
    cache.loadCache(null)
    new JsonBinaryCacheAsyncCrudRepo(cache, cacheName, ignite, identity)
  }
}