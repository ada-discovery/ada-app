package dataaccess.ignite

import java.io.Serializable
import java.util
import javax.cache.configuration.Factory
import javax.inject.Inject

import dataaccess.{FieldType, Field, AsyncCrudRepo}
import dataaccess.FieldType._
import org.apache.ignite.binary.BinaryObject
import org.apache.ignite.cache.{QueryIndex, QueryEntity, CacheAtomicityMode, CacheMode}
import org.apache.ignite.configuration.CacheConfiguration
import org.apache.ignite.{IgniteCache, Ignite}
import play.api.Logger
import play.api.libs.json.JsObject
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import dataaccess.ignite.BinaryJsonUtil.{escapeIgniteFieldName, getValueFromJson}

import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext.Implicits.global

class BinaryCacheFactory @Inject()(ignite: Ignite) extends Serializable {

  private val logger = Logger

  def apply[ID](
    repoFactory: Factory[AsyncCrudRepo[JsObject, ID]],
    getId: JsObject => Option[ID],
    idFieldName: String,
    cacheName: String,
    fieldNamesAndTypes: Seq[(String, FieldType.Value)])(
    implicit tagId: ClassTag[ID]
  ): IgniteCache[ID, BinaryObject] = {
    val cacheConfig = new CacheConfiguration[ID, BinaryObject]()

    cacheConfig.setName(cacheName)
    cacheConfig.setCacheMode(CacheMode.PARTITIONED)
    cacheConfig.setAtomicityMode(CacheAtomicityMode.ATOMIC)

    val fieldNameTypeMap = fieldNamesAndTypes match {
      case _ => {
        // if no fieldRepo provided fail over to a back-up plan and obtain the field types from the first row of the provided data set... this is however not recommended
        logger.warn(s"No dictionary (field repo) provided for the JSON data cache '$cacheName'. Going to obtain the fields and types from the first row of the data set.")
        Await.result(
          createFieldNameTypeMapFromDataSet(idFieldName, repoFactory.create),
          2 minutes
        )
      }
//      case _ =>
//        createFieldNameTypeMapFromDictionary(idFieldName, fieldNamesAndTypes)
    }

    val fieldNameClassMap = createFieldNameClassMapFromDictionary(idFieldName, fieldNamesAndTypes)

//      fieldNameTypeMap.map{ case (fieldName, typeName) =>
//      (fieldName,
//        if (typeName.equals("scala.Enumeration.Value"))
//          classOf[String].asInstanceOf[Class[Any]]
//        else
//          Class.forName(typeName).asInstanceOf[Class[Any]])
//    }

    val queryEntity = new QueryEntity() {
      setKeyType(tagId.runtimeClass.getName)
      setValueType(cacheName)
//      setIndexes(fieldNames.map(fieldName => new QueryIndex(fieldName)))
      setFields(new util.LinkedHashMap[String, String](fieldNameTypeMap))
    }

    cacheConfig.setQueryEntities(Seq(queryEntity))
    cacheConfig.setWriteThrough(true)
    cacheConfig.setReadThrough(true)

    val cacheStoreFactory = new BinaryCacheCrudRepoStoreFactory[ID](cacheName, ignite, repoFactory, getId, fieldNameClassMap)
    cacheConfig.setCacheStoreFactory(cacheStoreFactory)

    ignite.createCache(cacheConfig).withKeepBinary()
  }

  private def createFieldNameTypeMapFromDataSet[ID](
    idFieldName: String,
    dataRepo: AsyncCrudRepo[JsObject, ID])(
    implicit tagId: ClassTag[ID]
  ): Future[Map[String, String]] = {
    val firstElementFuture = dataRepo.find(limit = Some(1)).map(_.headOption)

    firstElementFuture.map( firstElement =>
      firstElement.get.fields.map { case (fieldName, jsValue) =>
        val fieldType = if (fieldName.equals(idFieldName))
          tagId.runtimeClass.getName
        else {
//          val value = getValueFromJson(jsValue)
//          if (value != null)
//            value.getClass.getName
//          else
            classOf[String].getName
        }

        (escapeIgniteFieldName(fieldName), fieldType)
      }.toMap
    )
  }

  private def createFieldNameTypeMapFromDictionary[ID](
    idFieldName: String,
    fieldNamesAndTypes: Seq[(String, FieldType.Value)])(
    implicit tagId: ClassTag[ID]
  ): Map[String, String] =
    (
      fieldNamesAndTypes.map{ case (fieldName, fieldType) =>
        (escapeIgniteFieldName(fieldName), getClassForFieldType(fieldType).getName)
      } ++
        Seq((idFieldName, tagId.runtimeClass.getName))
    ).toMap

  private def createFieldNameClassMapFromDictionary[ID](
    idFieldName: String,
    fieldNamesAndTypes: Seq[(String, FieldType.Value)])(
    implicit tagId: ClassTag[ID]
  ): Map[String, Class[_ >: Any]] =
    (
      fieldNamesAndTypes.map{ case (fieldName, fieldType) =>
        (escapeIgniteFieldName(fieldName), getClassForFieldType(fieldType))
      } ++
        Seq((idFieldName, tagId.runtimeClass.asInstanceOf[Class[_ >: Any]]))
     ).toMap

  // TODO: move somewhere to utils
  private def getClassForFieldType(fieldType: FieldType.Value): Class[_ >: Any] =
    fieldType match {
      case Null => classOf[String].asInstanceOf[Class[_ >: Any]]
      case Boolean => classOf[Boolean].asInstanceOf[Class[_ >: Any]]
      case Double => classOf[Double].asInstanceOf[Class[_ >: Any]]
      case Integer => classOf[Integer].asInstanceOf[Class[_ >: Any]]
      case Enum => classOf[String].asInstanceOf[Class[_ >: Any]]
      case String => classOf[String].asInstanceOf[Class[_ >: Any]]
      case Date => classOf[java.util.Date].asInstanceOf[Class[_ >: Any]]
      case Json => classOf[JsObject].asInstanceOf[Class[_ >: Any]]
    }
}