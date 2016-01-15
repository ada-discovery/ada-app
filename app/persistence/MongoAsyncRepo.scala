package persistence

import javax.inject.Inject

import play.api.libs.iteratee.{ Concurrent, Enumerator }
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand.Count
import reactivemongo.api.collections.GenericQueryBuilder
import reactivemongo.api.indexes.{ IndexType, Index }
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future
import models.{Field, Identity}
import play.api.libs.json._
import reactivemongo.api._

protected class MongoAsyncReadonlyRepo[E: Format, ID: Format](
    collectionName : String,
    identityName : String
  ) extends AsyncReadonlyRepo[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import play.modules.reactivemongo.json._
  import play.modules.reactivemongo.json.collection.JSONCollection

  @Inject var reactiveMongoApi : ReactiveMongoApi = _

  /** Mongo collection deserializable to [E] */
  protected lazy val collection: JSONCollection = reactiveMongoApi.db.collection(collectionName)

  override def get(id: ID): Future[Option[E]] =
    collection.find(Json.obj(identityName -> id)).one[E]

  override def find(
    criteria: Option[JsObject],
    orderBy: Option[JsObject],
    projection : Option[JsObject],
    limit: Option[Int],
    page: Option[Int]
  ): Future[Traversable[E]] = {

    // handle criteria and projection (if any)
    val queryBuilder: GenericQueryBuilder[collection.pack.type] = collection.find(criteria.getOrElse(Json.obj()), projection.getOrElse(Json.obj()))

    // handle sort (if any)
    val queryBuilder2: GenericQueryBuilder[collection.pack.type] = orderBy match {
      case Some(orderBy) => queryBuilder.sort(orderBy)
      case None => queryBuilder
    }

    // handle pagination (if requested)
    val cursor: CursorProducer[E]#ProducedCursor = page match {
      case Some(page) => limit.map(l =>
        queryBuilder2.options(QueryOpts(page * l, l)).cursor[E]()
      ).getOrElse(
          throw new IllegalArgumentException("Limit is expected when page is provided.")
        )

      case None => queryBuilder.cursor[E]()
    }
    // TODO: What about cursor[E](readPreference = ReadPreference.primary)

    // handle the limit
    limit match {
      case Some(limit) => cursor.collect[List](limit)
      case None => cursor.collect[List]()
    }
  }

  override def count(criteria: Option[JsObject]): Future[Int] =
    criteria match {
      case Some(criteria) => collection.runCommand(Count(criteria)).map(_.value)
      case None => collection.count()
    }
}

protected class MongoAsyncRepo[E: Format, ID: Format](
    collectionName : String)(
    implicit identity: Identity[E, ID]
  ) extends MongoAsyncReadonlyRepo[E, ID](collectionName, identity.name) with AsyncRepo[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import play.modules.reactivemongo.json._

  override def save(entity: E): Future[ID] = {
    val id = identity.next
    val doc = Json.toJson(identity.set(entity, id)).as[JsObject]
    collection.insert(doc).map {
      case le if le.ok == true => id
      case le => throw new IllegalArgumentException(le.message)
    }
  }
}

protected class MongoAsyncCrudRepo[E: Format, ID: Format](
    collectionName : String)(
    implicit identity: Identity[E, ID]
  ) extends MongoAsyncRepo[E, ID](collectionName) with AsyncCrudRepo[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import play.modules.reactivemongo.json._

  override def update(entity: E): Future[String] = {
    val doc = Json.toJson(entity).as[JsObject]
    identity.of(entity).map{ id =>
      collection.update(Json.obj(identity.name -> id), doc) map {
        case le if le.ok == true => id.toString
        case le => throw new IllegalArgumentException(le.message)
      }
    }.getOrElse(
        throw new IllegalArgumentException("Id required for update.")
    )
  }

  override def updateCustom(id: ID, modifier : JsObject): Future[String] =
    collection.update(Json.obj(identity.name -> id), modifier) map {
      case le if le.ok == true => id.toString
      case le => throw new IllegalArgumentException(le.message)
    }

  override def delete(id: ID): Future[ID] = {
    collection.remove(Json.obj(identity.name -> id)) map {  // collection.remove(Json.obj(identity.name -> id), firstMatchOnly = true)
      case le if le.ok == true => id
      case le => throw new IllegalAccessException(le.message)
    }
  }

  override def deleteAll : Future[String] = {
    collection.remove(Json.obj()).map {
      case le if le.ok == true => "ok"
      case le => le.message
    }
  }
}

protected class MongoAsyncStreamRepo[E: Format, ID: Format](
    collectionName : String)(
    implicit identity: Identity[E, ID]
  ) extends MongoAsyncRepo[E, ID](collectionName) with AsyncStreamRepo[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import play.modules.reactivemongo.json._
  import play.modules.reactivemongo.json.collection.JSONCollection

  override lazy val stream: Enumerator[E] = {
    // ObjectIDs are linear with time, we only want events created after now.
    val since = BSONObjectID.generate
    val enumerator = Enumerator.flatten(for {
      coll <- cappedCollection
    } yield coll.find(Json.obj("_id" -> Json.obj("$gt" -> since)))
      .options(QueryOpts().tailable.awaitData)
      .cursor[E]
      .enumerate()
    )
    Concurrent.broadcast(enumerator)._1
  }

  private lazy val cappedCollection: Future[JSONCollection] = {
    val coll = collection
    coll.stats().flatMap {
      case stats if !stats.capped =>
        // The collection is not capped, so we convert it
        coll.convertToCapped(102400, Some(1000))
      case _ => Future.successful(true)
    }.recover {
      // The collection mustn't exist, create it
      case _ =>
        coll.createCapped(102400, Some(1000))
    }.map { _ =>
      coll.indexesManager.ensure(Index(
        key = Seq("_id" -> IndexType.Ascending),
        unique = true
      ))
      coll
    }
  }
}

object CriteriaJSONWriter extends Writes[Map[String, Any]] {
  override def writes(criteria: Map[String, Any]): JsObject = JsObject(criteria.mapValues(toJsValue(_)).toSeq)
  val toJsValue: PartialFunction[Any, JsValue] = {
    case v: String => JsString(v)
    case v: Int => JsNumber(v)
    case v: Long => JsNumber(v)
    case v: Double => JsNumber(v)
    case v: Boolean => JsBoolean(v)
    case obj: JsValue => obj
    case map: Map[String, Any] @unchecked => CriteriaJSONWriter.writes(map)
    case coll: Traversable[_] => JsArray(coll.map(toJsValue(_)).toSeq)
    case null => JsNull
    case other => throw new IllegalArgumentException(s"Criteria value type not supported: $other")
  }
}