package services.ignite

import java.io.Serializable
import javax.cache.Cache.Entry
import javax.cache.configuration.Factory

import org.apache.ignite.cache.store.CacheStoreAdapter
import org.apache.ignite.lang.IgniteBiInClosure
import org.apache.ignite.lifecycle.LifecycleAware
import persistence.{RepoSynchronizer, AsyncCrudRepo}
import scala.concurrent.duration._
import play.api.libs.json.{JsResult, JsValue, Reads, Format}

protected class CacheCrudRepoStoreAdapter[ID, E](repoFactory: Factory[AsyncCrudRepo[E, ID]], getId: E => Option[ID]) extends CacheStoreAdapter[ID, E] with Serializable {

  private val crudRepo: AsyncCrudRepo[E, ID] = repoFactory.create
  private lazy val syncRepo = RepoSynchronizer(crudRepo, 2 minutes)

  override def delete(key: scala.Any): Unit = {
    syncRepo.delete(key.asInstanceOf[ID])
  }

  override def write(entry: Entry[_ <: ID, _ <: E]): Unit = {
    syncRepo.save(entry.getValue)
  }

  override def load(key: ID): E = {
    syncRepo.get(key).getOrElse(null.asInstanceOf[E])
  }

  override def loadCache(clo: IgniteBiInClosure[ID, E], args: AnyRef *): Unit = {
    syncRepo.find().map( item =>
      getId(item).map( id => clo.apply(id, item))
    )
  }

//  override def loadAll() = {
//
//  } //, writeAll(), and deleteAll()
}