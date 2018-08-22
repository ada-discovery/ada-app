package dataaccess.ignite

import org.apache.ignite.cache.store.CacheStore

import scala.reflect.runtime.universe._
import java.io.Serializable
import javax.cache.configuration.Factory
import javax.inject.Inject

import org.incal.core.dataaccess.AsyncCrudRepo
import org.incal.core.util.ReflectionUtil._
import org.apache.ignite.cache.{QueryIndex, QueryEntity, CacheAtomicityMode, CacheMode}
import org.apache.ignite.configuration.{BinaryConfiguration, CacheConfiguration}
import org.apache.ignite.{Ignite, IgniteCache}
import scala.collection.JavaConversions._

import scala.reflect.ClassTag

class CacheFactory @Inject()(ignite: Ignite) extends Serializable {

  def apply[ID, E](
    cacheName: String,
    repoFactory: Factory[AsyncCrudRepo[E, ID]],
    getId: E => Option[ID])(
    implicit tagId: ClassTag[ID], typeTagE: TypeTag[E]
  ): IgniteCache[ID, E] =
    apply(
      cacheName,
      Some(new CacheCrudRepoStoreFactory[ID, E](repoFactory, getId))
    )

  def apply[ID, E](
    cacheName: String,
    cacheStoreFactoryOption: Option[Factory[CacheStore[ID, E]]])(
    implicit tagId: ClassTag[ID], typeTagE: TypeTag[E]
  ): IgniteCache[ID, E] = {
    val cacheConfig = new CacheConfiguration[ID, E]()

    val fieldNamesAndTypes = getCaseClassMemberAndTypeNames[E]
    val fieldNames = fieldNamesAndTypes.map(_._1)
    val fields = fieldNamesAndTypes.toMap

    val queryEntity = new QueryEntity() {
      setKeyType(tagId.runtimeClass.getName)
      setValueType(typeOf[E].typeSymbol.fullName)
      setFields(new java.util.LinkedHashMap[String, String](fields))
      setIndexes(fieldNames.map(new QueryIndex(_)).toSeq)
    }

    cacheConfig.setSqlFunctionClasses(classOf[CustomSqlFunctions])
    cacheConfig.setName(cacheName)
    cacheConfig.setQueryEntities(Seq(queryEntity))
    cacheConfig.setCacheMode(CacheMode.LOCAL) //  PARTITIONED
    cacheConfig.setAtomicityMode(CacheAtomicityMode.ATOMIC)

    cacheStoreFactoryOption.foreach{ cacheStoreFactory =>
      cacheConfig.setCacheStoreFactory(cacheStoreFactory)
      cacheConfig.setWriteThrough(true)
      cacheConfig.setReadThrough(true)
    }

//    val bCfg = new BinaryConfiguration()
//    bCfg.setIdMapper(new BinaryBasicIdMapper)
//    bCfg.setTypeConfigurations(util.Arrays.asList(new BinaryTypeConfiguration("org.my.Class")))

    ignite.getOrCreateCache(cacheConfig) // .withKeepBinary()
  }
}