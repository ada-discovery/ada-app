package dataaccess.elastic

import com.evojam.play.elastic4s.PlayElasticJsonSupport
import com.sksamuel.elastic4s.{RichGetResponse, RichSearchHit, RichSearchResponse}
import dataaccess.DynamicConstructorFinder
import play.api.libs.json.{Format, JsNull, JsObject, Json}

import collection.mutable.{Map => MMap}
import scala.reflect.runtime.{universe => ru}
import java.util.Date

trait ElasticSerializer[E] {

  protected def serializeGetResult(
    response: RichGetResponse
  ): Option[E]

  protected def serializeSearchResult(
    response: RichSearchResponse
  ): Traversable[E]

  protected def serializeProjectionSearchResult(
    projection: Seq[String],
    result: Traversable[(String, Any)]
  ): E

  // by default just iterate through and serialize each result independently
  protected def serializeProjectionSearchHits(
    projection: Seq[String],
    results: Array[RichSearchHit]
  ): Traversable[E] =
    results.map { serializeProjectionSearchHit(projection, _) }

  protected def serializeProjectionSearchHit(
    projection: Seq[String],
    result: RichSearchHit
  ): E = {
    val fieldValues = result.fieldsSeq.map(field => (field.name, field.getValue[Any]))
    serializeProjectionSearchResult(projection, fieldValues)
  }

  protected def serializeSearchHit(
    result: RichSearchHit
  ): E
//
//  {
//    val fieldValues = result.fieldsSeq.map(field => (field.name, field.getValue[Any]))
//    serializeProjectionSearchResult(fieldValues.map(_._1), fieldValues)
//  }
}

trait ElasticFormatSerializer[E] extends ElasticSerializer[E] with PlayElasticJsonSupport {

  protected implicit val format: Format[E]
  protected implicit val manifest: Manifest[E]

  private val concreteClassFieldName = "concreteClass"

  // because of JSON conversion Elastic stores dates as numbers, so we need to convert it back
  // TODO: we need to do a similar thing for BSONObjectId... see ElasticAsyncReadonlyRepo.toDBValue
  private val typeValueConverters: Seq[(ru.Type, Any => Any)] =
    Seq((
      ru.typeOf[Date],
      (value: Any) =>
        value match {
          case ms: Long => new Date(ms)
          case ms: Int => new Date(ms)
          case _ => value
        }
    ))

  protected val defaultConstructorFinder = DynamicConstructorFinder.apply[E]
  protected val classNameConstructorFinderMap = MMap[String, DynamicConstructorFinder[E]]()

  override protected def serializeGetResult(response: RichGetResponse): Option[E] =
    response.as[E]

  override protected def serializeSearchResult(response: RichSearchResponse): Traversable[E] =
    response.as[E]

  override protected def serializeProjectionSearchResult(
    projection: Seq[String],
    result: Traversable[(String, Any)]
  ) = {
    val fieldNameValueMap = result.toMap

    val constructor =
      constructorOrException(
        projection,
        fieldNameValueMap.get(concreteClassFieldName).map(_.asInstanceOf[String])
      )

    constructor(fieldNameValueMap).get
  }

  override protected def serializeProjectionSearchHits(
    projection: Seq[String],
    results: Array[RichSearchHit]
  ): Traversable[E] =
    if (!projection.contains(concreteClassFieldName)) {
      val constructor = constructorOrException(projection)
      results.map { result =>
        val fieldValues = result.fieldsSeq.map(field => (field.name, field.getValue[Any]))
        constructor(fieldValues.toMap).get
      }
    } else
      // TODO: optimize me... we should group the results by the concrete class field name
      results.map { serializeProjectionSearchHit(projection, _) }

  override protected def serializeSearchHit(
    result: RichSearchHit
  ): E = Json.parse(result.sourceAsString).as[E]

  protected def constructorOrException(
    fieldNames: Seq[String],
    concreteClassName: Option[String] = None
  ) = {
    val unrenamedFieldNames = fieldNames.map( fieldName =>
      ElasticIdRenameUtil.unrename(fieldName)
    )

    val constructorFinder =
      // if concrete class name is defined check the map (cache) or create a new constructor using the class name
      concreteClassName.map { className =>
        classNameConstructorFinderMap.getOrElseUpdate(
          className,
          DynamicConstructorFinder.apply(className)
        )
      // otherwise use the default one, which opts to call constructor of the core class associated with a type E
      }.getOrElse(
        defaultConstructorFinder
      )

    constructorFinder(unrenamedFieldNames, typeValueConverters).getOrElse {
      throw new IllegalArgumentException(s"No constructor of the class '${constructorFinder.classSymbol.fullName}' matches the query result fields '${fieldNames.mkString(", ")}'. Adjust your query or introduce an appropriate constructor.")
    }
  }
}