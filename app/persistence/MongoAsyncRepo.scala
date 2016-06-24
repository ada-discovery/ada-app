package persistence

import javax.inject.Inject

import play.api.libs.iteratee.{ Concurrent, Enumerator }
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.collections.GenericQueryBuilder
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{ IndexType, Index }
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand.Count
import reactivemongo.core.commands.Sort
import scala.concurrent.duration._
import scala.concurrent.Future
import models.{Field, Identity}
import play.api.libs.json._
import reactivemongo.api._

protected class MongoAsyncReadonlyRepo[E: Format, ID: Format](
    collectionName : String,
    val identityName : String
  ) extends AsyncReadonlyRepo[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import play.modules.reactivemongo.json._
  import play.modules.reactivemongo.json.collection.JSONCollection

  @Inject var reactiveMongoApi : ReactiveMongoApi = _

  private val failoverStrategy =
    FailoverStrategy(
      initialDelay = 5 seconds,
      retries = 5,
      delayFactor =
        attemptNumber => 1 + attemptNumber * 0.5
    )

  protected lazy val collection: JSONCollection = reactiveMongoApi.db.collection(collectionName, failoverStrategy)

  override def get(id: ID): Future[Option[E]] =
    collection.find(Json.obj(identityName -> id)).one[E]

  override def find(
    criteria: Option[JsObject],
    orderBy: Option[Seq[Sort]],
    projection : Option[JsObject],
    limit: Option[Int],
    page: Option[Int]
  ): Future[Traversable[E]] = {

    // handle criteria and projection (if any)
    val queryBuilder: GenericQueryBuilder[collection.pack.type] = collection.find(criteria.getOrElse(Json.obj()), projection.getOrElse(Json.obj()))

    // handle sort (if any)
    val queryBuilder2: GenericQueryBuilder[collection.pack.type] = orderBy match {
      case Some(orderBy) => queryBuilder.sort(toJsonSort(orderBy))
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

  private def toJsonSort(sorts: Seq[Sort]) = {
    val jsonSorts = sorts.map{
      _ match {
        case AscSort(fieldName) => (fieldName -> JsNumber(1))
        case DescSort(fieldName) => (fieldName -> JsNumber(-1))
    }}
    JsObject(jsonSorts)
  }

  override def count(criteria: Option[JsObject]): Future[Int] =
    criteria match {
      case Some(criteria) => collection.runCommand(Count(criteria)).map(_.value)
      case None => collection.count()
    }

  protected def handleResult(result : WriteResult) =
    if (!result.ok) throw new RepoException(result.message)
}

protected class MongoAsyncRepo[E: Format, ID: Format](
    collectionName : String)(
    implicit identity: Identity[E, ID]
  ) extends MongoAsyncReadonlyRepo[E, ID](collectionName, identity.name) with AsyncRepo[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import play.modules.reactivemongo.json._

  override def save(entity: E): Future[ID] = {
    val (doc, id) = toJsonAndId(entity)

    collection.insert(doc).map {
      case le if le.ok => id
      case le => throw new RepoException(le.message)
    }
  }

  override def save(entities: Traversable[E]): Future[Traversable[ID]] = {
    println("SAving in bulk mongo...")
    val docAndIds = entities.map(toJsonAndId)

    collection.bulkInsert(docAndIds.map(_._1).toStream, ordered = false).map { // bulkSize = 100, bulkByteSize = 16793600
      case le if le.ok => docAndIds.map(_._2)
      case le => throw new RepoException(le.errmsg.getOrElse(""))
    }
  }

  private def toJsonAndId(entity: E): (JsObject, ID) = {
    val givenId = identity.of(entity)
    if (givenId.isDefined) {
      val doc = Json.toJson(entity).as[JsObject]
      (doc, givenId.get)
    } else {
      val id = identity.next
      val doc = Json.toJson(identity.set(entity, id)).as[JsObject]
      (doc, id)
    }
  }
}

protected class MongoAsyncCrudRepo[E: Format, ID: Format](
    collectionName : String)(
    implicit identity: Identity[E, ID]
  ) extends MongoAsyncRepo[E, ID](collectionName) with MongoAsyncCrudExtraRepo[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import play.modules.reactivemongo.json._
  import play.modules.reactivemongo.json.commands.JSONAggregationFramework.{Match, Group, GroupFunction, Unwind, Sort => AggSort, Limit, Skip, Project, SumField, Push, SortOrder, Ascending, Descending}

  override def update(entity: E): Future[ID] = {
    val doc = Json.toJson(entity).as[JsObject]
    identity.of(entity).map{ id =>
      collection.update(Json.obj(identity.name -> id), doc) map {
        case le if le.ok => id
        case le => throw new RepoException(le.message)
      }
    }.getOrElse(
        throw new RepoException("Id required for update.")
    )
  }

  // collection.remove(Json.obj(identity.name -> id), firstMatchOnly = true)
  override def delete(id: ID): Future[Unit] =
    collection.remove(Json.obj(identity.name -> id)) map handleResult

  override def deleteAll: Future[Unit] =
    collection.remove(Json.obj()) map handleResult

  // extra functions which should not be exposed beyond the persistence layer
  override protected[persistence] def updateCustom(
    selector: JsObject,
    modifier : JsObject
  ): Future[Unit] =
    collection.update(selector, modifier) map handleResult

  override protected[persistence] def findAggregate(
    criteria: Option[JsObject],
    orderBy: Option[Seq[Sort]],
    projection : Option[JsObject],
    idGroup : Option[JsValue],
    groups : Option[Seq[(String, GroupFunction)]],
    unwindFieldName : Option[String],
    limit: Option[Int],
    page: Option[Int]
  ): Future[Traversable[JsObject]] = {

    val params = List(
      unwindFieldName.map(Unwind(_)),                            // $unwind
      criteria.map(Match(_)),                                    // $match
      projection.map(Project(_)),                                // $project
      orderBy.map(sort => AggSort(toAggregateSort(sort): _ *)),  // $sort
      page.map(page  => Skip(page * limit.getOrElse(0))),        // $skip
      limit.map(Limit(_)),                                       // $limit
      idGroup.map(id => Group(id)(groups.get: _*))               // $group
    ).flatten

    val result = collection.aggregate(params.head, params.tail, false, true)

    result.map(_.documents)
  }

  private def toAggregateSort(sorts: Seq[Sort]) =
    sorts.map{
      _ match {
        case AscSort(fieldName) => Ascending(fieldName)
        case DescSort(fieldName) => Descending(fieldName)
      }}
}

trait MongoAsyncCrudExtraRepo[E, ID] extends AsyncCrudRepo[E, ID] {
  import play.modules.reactivemongo.json.commands.JSONAggregationFramework.{GroupFunction, SortOrder}

  /*
   * Special aggregate function closely tight to Mongo db functionality.
   *
   * Should be used only for special cases (only within the persistence layer)!
   */
  protected[persistence] def findAggregate(
    criteria: Option[JsObject],
    orderBy: Option[Seq[Sort]],
    projection : Option[JsObject],
    idGroup : Option[JsValue],
    groups : Option[Seq[(String, GroupFunction)]],
    unwindFieldName : Option[String],
    limit: Option[Int],
    page: Option[Int]
  ): Future[Traversable[JsObject]]

  /*
   * Special update function expecting a modifier specified as a JSON object closely tight to Mongo db functionality
   *
   * should be used only for special cases (only within the persistence layer)!
   */
  protected[persistence] def updateCustom(
    selector: JsObject,
    modifier : JsObject
  ): Future[Unit]
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