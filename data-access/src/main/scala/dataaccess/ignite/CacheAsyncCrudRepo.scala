package dataaccess.ignite

import javax.cache.configuration.Factory
import javax.inject.Inject

import dataaccess.ReflectionUtil.shortName
import dataaccess.mongo.{ReactiveMongoApi, MongoAsyncCrudRepo}
import dataaccess.{SerializableApplicationLifecycle, Identity, AsyncCrudRepo}
import dataaccess.ignite.BinaryJsonUtil.unescapeFieldName
import org.apache.ignite.{Ignite, IgniteCache}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsResult, JsValue, Format}
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

import scala.reflect.ClassTag

private class CacheAsyncCrudRepo[ID, E: TypeTag](
    cache: IgniteCache[ID, E],
    entityName: String,
    identity: Identity[E, ID]
  ) extends AbstractCacheAsyncCrudRepo[ID, E, ID, E](cache, entityName, identity) {

  // TODO: move all reflection stuff to ReflectionUtil
  private val defaultTypeValues = Map[Type, Any](
    typeOf[Option[_]] -> None,
    typeOf[Boolean] -> false,
    typeOf[Seq[_]] -> Nil,
    typeOf[Set[_]] -> Set(),
    typeOf[Map[_, _]] -> Map()
  )

  private val m = ru.runtimeMirror(getClass.getClassLoader)
  private val reflectedClass = typeOf[E].typeSymbol.asClass
  private val cm = m.reflectClass(reflectedClass)

  private val constructorWithInfos = ru.typeOf[E].decl(ru.termNames.CONSTRUCTOR).asTerm.alternatives.map{ ctor =>
    val constructor = cm.reflectConstructor(ctor.asMethod)
    val paramNameAndTypes = ctor.asMethod.paramLists.map(_.map{x => (shortName(x), x.info)}).flatten
    (paramNameAndTypes, constructor)
  }.sortBy(-_._1.size)

  // hooks
  override def toCacheId(id: ID) =
    id

  override def toItem(cacheItem: E) =
//    cacheItem.asInstanceOf[BinaryObject].deserialize[E]
    cacheItem

  override def toCacheItem(item: E) =
    item

  override def queryResultToItem(result: Seq[(String, Any)]) = {
    val fieldNameValueMap = result.map{ case (fieldName, value) => (unescapeFieldName(fieldName), value) }.toMap
    val multiConstructorValues = constructorWithInfos.map { case (paramNameAndTypes, ctorm) =>
      try {
        val constructorValues = paramNameAndTypes.map { case (paramName, paramType) =>
          fieldNameValueMap.get(paramName).getOrElse {
            // failing over to default values
            val defaultValue = defaultTypeValues.find {
              case (defaultType, defaultValue) => paramType <:< defaultType
            }.map(_._2)

            defaultValue.getOrElse(
              throw new IllegalArgumentException(s"Constructor of ${reflectedClass.fullName} expects mandatory param '${paramName}' but the result set contains none.")
            )
          }
        }
        Some(constructorValues, ctorm)
      } catch {
        case e: Exception => None
      }
    }.flatten

    // TODO: choosing the first constructor (the one that satisfies the most parameters... see sorting in the declaration); alternatively could throw an exception or log a warning saying that multiple constructors could be applied
    multiConstructorValues.headOption.map { case (constructorValues, constructor) =>
      constructor(constructorValues: _*).asInstanceOf[E]
    }.getOrElse{
      val resultFieldNames = result.map(_._1).mkString(", ")
      throw new IllegalArgumentException(s"No constructor of the class '${reflectedClass.fullName}' matches the query result fields '${resultFieldNames}'. Adjust your query or introduce an appropriate constructor.")
    }
  }
}

class CacheAsyncCrudRepoFactory {

  @Inject var cacheFactory: CacheFactory = _
  @Inject var configuration: Configuration = _
  private val applicationLifecycle = new SerializableApplicationLifecycle()

  def apply[ID: ClassTag, E: TypeTag](
    repoFactory: Factory[AsyncCrudRepo[E, ID]],
    cacheName: String)(
    implicit identity: Identity[E, ID]
  ): AsyncCrudRepo[E, ID] = {
    val cache = cacheFactory[ID, E](
      repoFactory,
      identity.of,
      cacheName
    )
    cache.loadCache(null)
    val entityName = shortName(typeOf[E].typeSymbol)
    new CacheAsyncCrudRepo(cache, entityName, identity)
  }

  // Important: instead of passing Format with need to decompose (accept) only reads and writes functions
  def applyMongo[ID: ClassTag, E: TypeTag](
    mongoCollectionName: String,
    cacheName: Option[String] = None)(
    implicit formatId: Format[ID], formatE: Format[E], identity: Identity[E, ID]
  ): AsyncCrudRepo[E, ID] = {
    val repoFactory = new MongoAsyncCrudRepoFactory[E, ID](mongoCollectionName, configuration, applicationLifecycle)
    apply(repoFactory, cacheName.getOrElse(mongoCollectionName))
  }
}

private class MongoAsyncCrudRepoFactory[E, ID](
    collectionName: String,
    configuration: Configuration,
    applicationLifecycle: ApplicationLifecycle)(
    implicit formatId: Format[ID], formatE: Format[E], identity: Identity[E, ID]
  ) extends Factory[AsyncCrudRepo[E, ID]] {

  override def create(): AsyncCrudRepo[E, ID] = {
    val repo = new MongoAsyncCrudRepo[E, ID](collectionName)
    repo.reactiveMongoApi = ReactiveMongoApi.create(configuration, applicationLifecycle)
    repo
  }
}