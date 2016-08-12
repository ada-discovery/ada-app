package dataaccess.ignite

import java.io.Serializable
import javax.cache.Cache.Entry
import javax.cache.configuration.Factory

import dataaccess.{AsyncCrudRepo, RepoSynchronizer}
import org.apache.ignite.cache.store.{CacheStore, CacheStoreAdapter}
import org.apache.ignite.lang.IgniteBiInClosure
import play.api.Logger
import scala.concurrent.duration._

protected class CacheCrudRepoStoreAdapter[ID, E](repoFactory: Factory[AsyncCrudRepo[E, ID]], getId: E => Option[ID]) extends CacheStoreAdapter[ID, E] with Serializable {

  private val logger = Logger
  private val crudRepo: AsyncCrudRepo[E, ID] = repoFactory.create
  private lazy val syncRepo = RepoSynchronizer(crudRepo, 2 minutes)

  override def delete(key: scala.Any): Unit = {
    syncRepo.delete(key.asInstanceOf[ID])
  }

  override def write(entry: Entry[_ <: ID, _ <: E]): Unit = {
    val id = entry.getKey
    val item = entry.getValue

    // TODO: replace with a single upsert (save/update) call
    getId(item) match {
      case Some(id) => {
        syncRepo.get(id) match {
          case Some(_) => {
            logger.info(s"Updating an item of type ${item.getClass.getSimpleName}")
            syncRepo.update(item)
          }
          case None => {
            logger.info(s"Saving an item of type ${item.getClass.getSimpleName}")
            syncRepo.save(item)
          }
        }
      }
      case None => {
        logger.info(s"Saving an item of type ${item.getClass.getSimpleName}")
        syncRepo.save(item)
      }
    }
  }

  override def load(key: ID): E = {
    logger.info(s"Loading item for key of type ${key.getClass.getSimpleName}")
    syncRepo.get(key).getOrElse(null.asInstanceOf[E])
  }

  override def loadCache(clo: IgniteBiInClosure[ID, E], args: AnyRef *): Unit = {
    logger.info("Loading Cache")
    syncRepo.find().foreach( item =>
      getId(item).map{ id =>
        clo.apply(id, item)
      }
    )
  }

//  override def loadAll() = {
//
//  } //, writeAll(), and deleteAll()
}

protected class CacheCrudRepoStoreFactory[ID, E](
    repoFactory: Factory[AsyncCrudRepo[E, ID]],
    getId: E => Option[ID]
  ) extends Factory[CacheStore[ID, E]] {

  override def create(): CacheStore[ID, E] =
    new CacheCrudRepoStoreAdapter[ID, E](repoFactory, getId)
}