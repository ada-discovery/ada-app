package org.ada.server.dataaccess.ignite

import org.ada.server.models.User
import org.apache.ignite.cache.store.CacheStoreAdapter
import org.apache.ignite.lang.IgniteBiInClosure
import org.incal.core.dataaccess.{AsyncCrudRepo, RepoSynchronizer}
import play.api.Logger

import java.io.Serializable
import java.util
import javax.cache.Cache.Entry
import javax.cache.configuration.Factory
import scala.collection.JavaConversions._
import scala.concurrent.duration._

//@CacheLocalStore
abstract class AbstractCacheAsyncCrudRepoProvider[ID, E, REPO_E] extends CacheStoreAdapter[ID, E] with Serializable {

  val repoFactory: Factory[AsyncCrudRepo[REPO_E, ID]]
  def toRepoItem: E => REPO_E
  def fromRepoItem: REPO_E => E
  def getId: REPO_E => Option[ID]

  private val logger = Logger
  private val crudRepo: AsyncCrudRepo[REPO_E, ID] = repoFactory.create
  private lazy val syncRepo = RepoSynchronizer(crudRepo, 2 minutes)

  private val userClassName = User.getClass.getSimpleName.replace("$","")

  override def delete(key: Any) =
    syncRepo.delete(key.asInstanceOf[ID])

  override def deleteAll(keys: util.Collection[_]) =
    syncRepo.delete(keys.map(_.asInstanceOf[ID]))

  override def write(entry: Entry[_ <: ID, _ <: E]): Unit = {
    val id = entry.getKey
    val item = toRepoItem(entry.getValue)

    def trackWritingOps(className: String, operationType: String, item: REPO_E, result: Option[REPO_E] = None): Unit = {
      logger.info(s"$operationType an item of type $className")
      if (className.equalsIgnoreCase(userClassName))
        logger.info(s"$operationType -> $item . Found in cache $result")
    }

    syncRepo.get(id)
      .map( result => {
        trackWritingOps(item.getClass.getSimpleName, "Updating", item, Option(result))
        syncRepo.update(item)
      })
      .getOrElse{
        trackWritingOps(item.getClass.getSimpleName, "Saving", item)
        syncRepo.save(item)
      }

  }

  override def writeAll(entries: util.Collection[Entry[_ <: ID, _ <: E]]): Unit = {
    val ids = entries.map(_.getKey)
    val items = entries.map(entry => toRepoItem(entry.getValue))

    if (items.nonEmpty) {
      // TODO: save vs update
      logger.info(s"Saving ${items.size} items of type ${items.head.getClass.getSimpleName}")
      syncRepo.save(items)
    }
  }

  override def load(key: ID): E = {
    logger.info(s"Loading item for key of type ${key.getClass.getSimpleName}")
    syncRepo.get(key).map(fromRepoItem).getOrElse(null.asInstanceOf[E])
  }

  override def loadCache(clo: IgniteBiInClosure[ID, E], args: AnyRef *) = {
    logger.info("Loading Cache")
    syncRepo.find().foreach( item =>
      getId(item).map(clo.apply(_, fromRepoItem(item)))
    )
  }

  //  override def loadAll() = {
}