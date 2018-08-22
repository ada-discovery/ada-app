package dataaccess.ignite

import java.io.Serializable
import javax.cache.Cache.Entry
import javax.cache.configuration.Factory

import org.incal.core.dataaccess._
import org.apache.ignite.Ignite
import org.apache.ignite.binary.{BinaryObject, BinaryType}
import org.apache.ignite.cache.store.{CacheStore, CacheStoreAdapter}
import org.apache.ignite.lang.IgniteBiInClosure
import org.apache.ignite.resources.IgniteInstanceResource
import play.api.Logger
import play.api.libs.json.{JsObject, Json, JsValue}
import scala.collection.JavaConversions._
import dataaccess.ignite.BinaryJsonUtil._

import scala.concurrent.duration._

protected class BinaryCacheCrudRepoStoreAdapter[ID](
    cacheName: String,
    repoFactory: Factory[AsyncCrudRepo[JsObject, ID]],
    getId: JsObject => Option[ID],
    fieldNameClassMap: Map[String, Class[_ >: Any]]
  ) extends CacheStoreAdapter[ID, BinaryObject] with Serializable {

  private val logger = Logger
  private val crudRepo: AsyncCrudRepo[JsObject, ID] = repoFactory.create
  private lazy val syncRepo = RepoSynchronizer(crudRepo, 2 minutes)

  @IgniteInstanceResource
  private var ignite: Ignite = _
  private lazy val toBinary = toBinaryObject(ignite.binary(), fieldNameClassMap, cacheName)_

  override def delete(key: scala.Any): Unit = {
    syncRepo.delete(key.asInstanceOf[ID])
  }

  override def write(entry: Entry[_ <: ID, _ <: BinaryObject]): Unit = {
    val binaryObject = entry.getValue
    syncRepo.save(toJsObject(binaryObject))
  }

  override def load(key: ID): BinaryObject =
    syncRepo.get(key).map(toBinary).
      getOrElse(null.asInstanceOf[BinaryObject])

  override def loadCache(clo: IgniteBiInClosure[ID, BinaryObject], args: AnyRef *): Unit = {
    logger.info(s"Loading Cache $cacheName")
    syncRepo.find().map( item =>
      getId(item).map( id =>
        clo.apply(id, toBinary(item))
      )
    )
  }
}

protected class BinaryCacheCrudRepoStoreFactory[ID](
    cacheName: String,
    ignite: Ignite,
    repoFactory: Factory[AsyncCrudRepo[JsObject, ID]],
    getId: JsObject => Option[ID],
    fieldNameClassMap: Map[String, Class[_ >: Any]]
  ) extends Factory[CacheStore[ID, BinaryObject]] {

  override def create(): CacheStore[ID, BinaryObject] =
    new BinaryCacheCrudRepoStoreAdapter[ID](cacheName, repoFactory, getId, fieldNameClassMap)
}