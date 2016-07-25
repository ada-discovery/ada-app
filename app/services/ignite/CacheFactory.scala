package services.ignite

import java.io.Serializable
import javax.inject.Inject
import org.apache.commons.lang3.RandomStringUtils
import org.apache.ignite.cache.store.CacheStore
import org.apache.ignite.{IgniteCache, Ignite}
import org.apache.ignite.cache.{CacheAtomicityMode, CacheMode}
import org.apache.ignite.configuration.CacheConfiguration
import persistence.AsyncCrudRepo
import javax.cache.configuration.Factory

class CacheFactory @Inject() (ignite: Ignite) extends Serializable {

  def apply[ID, E](repoFactory: Factory[AsyncCrudRepo[E, ID]], getId: E => Option[ID], cacheName: Option[String] = None): IgniteCache[ID, E] = {
    val cacheStoreFactory = new CacheCrudRepoStoreFactory[ID, E](repoFactory, getId)
    val cacheConfig = new CacheConfiguration[ID, E](cacheName.getOrElse(RandomStringUtils.random(10)))

    cacheConfig.setCacheMode(CacheMode.PARTITIONED)
    cacheConfig.setAtomicityMode(CacheAtomicityMode.ATOMIC)
    cacheConfig.setCacheStoreFactory(cacheStoreFactory)
    cacheConfig.setWriteThrough(true)
    cacheConfig.setReadThrough(true)

    ignite.createCache(cacheConfig)
  }
}

protected class CacheCrudRepoStoreFactory[ID, E](repoFactory: Factory[AsyncCrudRepo[E, ID]], getId: E => Option[ID]) extends Factory[CacheStore[ID, E]] {
  override def create(): CacheStore[ID, E] =
    new CacheCrudRepoStoreAdapter[ID, E](repoFactory, getId)
}