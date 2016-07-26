package persistence

import javax.inject.Inject

import _root_.util.JsonUtil._
import play.api.libs.iteratee.{ Concurrent, Enumerator }
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.collections.GenericQueryBuilder
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{ IndexType, Index }
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand.Count
import scala.concurrent.duration._
import scala.concurrent.Future
import models._
import reactivemongo.api._

protected class MongoAsyncReadonlyRepo[E: Format, ID: Format](
    collectionName : String,
    val identityName : String
  ) extends AsyncReadonlyRepo[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import play.modules.reactivemongo.json._
  import play.modules.reactivemongo.json.collection.JSONCollection

  @Inject var reactiveMongoApi : ReactiveMongoApi = _

//  private val failoverStrategy =
//    FailoverStrategy(
//      initialDelay = 5 seconds,
//      retries = 5,
//      delayFactor =
//        attemptNumber => 1 + attemptNumber * 0.5
//    )

  protected lazy val collection: JSONCollection = reactiveMongoApi.db.collection[JSONCollection](collectionName)

  override def get(id: ID): Future[Option[E]] =
    collection.find(Json.obj(identityName -> id)).one[E]

  override def find(
    criteria: Seq[Criterion[Any]],
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    page: Option[Int]
  ): Future[Traversable[E]] = {
    val jsonProjection = JsObject(projection.map(fieldName => (fieldName, Json.toJson(1))).toSeq)
    val jsonCriteria = JsObject(criteria.map(toJsonCriterion(_)))

    // handle criteria and projection (if any)
    val queryBuilder: GenericQueryBuilder[collection.pack.type] = collection.find(jsonCriteria, jsonProjection)

    // handle sort (if any)
    val queryBuilder2: GenericQueryBuilder[collection.pack.type] = sort match {
      case Nil => queryBuilder
      case _ => queryBuilder.sort(toJsonSort(sort))
    }

    // handle pagination (if requested)
    val cursor: CursorProducer[E]#ProducedCursor = page match {
      case Some(page) => limit.map(l =>
        queryBuilder2.options(QueryOpts(page * l, l)).cursor[E]()
      ).getOrElse(
          throw new IllegalArgumentException("Limit is expected when page is provided.")
        )

      case None => queryBuilder2.cursor[E]()
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

  protected def toJsonCriterion[T, V](criterion: Criterion[T]): (String, JsValue) = {
    val fieldName = criterion.fieldName

    val mongoCondition = criterion match {
      case c: EqualsCriterion[T] => Json.toJson(c.value)(c.valueFormat)
      case RegexEqualsCriterion(_, value) => Json.obj("$regex" -> value, "$options" -> "i")
      case c: NotEqualsCriterion[T] => Json.obj("$ne" -> Json.toJson(c.value)(c.valueFormat))
      case c: InCriterion[V] => {
        val inValues = c.value.map(Json.toJson(_)(c.elementFormat): JsValueWrapper)
        Json.obj("$in" -> Json.arr(inValues: _*))
      }
      case c: NotInCriterion[V] => {
        val inValues = c.value.map(Json.toJson(_)(c.elementFormat): JsValueWrapper)
        Json.obj("$nin" -> Json.arr(inValues: _*))
      }
      case GreaterCriterion(_, value) => Json.obj("$gt" -> value)
      case LessCriterion(_, value) => Json.obj("$lt" -> value)
    }
    (fieldName, mongoCondition)
  }

  override def count(criteria: Seq[Criterion[Any]]): Future[Int] =
    criteria match {
      case Nil => collection.count()
      case _ => {
        val jsonCriteria = JsObject(criteria.map(toJsonCriterion(_)))
        collection.runCommand(Count(jsonCriteria)).map(_.value)
      }
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

class MongoAsyncCrudRepo[E: Format, ID: Format](
    collectionName : String)(
    implicit identity: Identity[E, ID]
  ) extends MongoAsyncRepo[E, ID](collectionName) with MongoAsyncCrudExtraRepo[E, ID] {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import play.modules.reactivemongo.json._
  import play.modules.reactivemongo.json.commands.JSONAggregationFramework.{Match, Group, GroupFunction, Unwind, Sort => AggSort, Cursor => AggCursor, Limit, Skip, Project, SumField, Push, SortOrder, Ascending, Descending}

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
    rootCriteria: Seq[Criterion[Any]],
    subCriteria: Seq[Criterion[Any]],
    sort: Seq[Sort],
    projection : Option[JsObject],
    idGroup : Option[JsValue],
    groups : Option[Seq[(String, GroupFunction)]],
    unwindFieldName : Option[String],
    limit: Option[Int],
    page: Option[Int]
  ): Future[Traversable[JsObject]] = {
    val jsonRootCriteria = rootCriteria.headOption.map(_ => JsObject(rootCriteria.map(toJsonCriterion(_))))
    val jsonSubCriteria = subCriteria.headOption.map(_ => JsObject(subCriteria.map(toJsonCriterion(_))))

    val params = List(
      jsonRootCriteria.map(Match(_)),                                 // $match
      unwindFieldName.map(Unwind(_)),                                 // $unwind
      jsonSubCriteria.map(Match(_)),                                  // $match
      projection.map(Project(_)),                                     // $project
      sort.headOption.map(_ => AggSort(toAggregateSort(sort): _ *)),  // $sort
      page.map(page  => Skip(page * limit.getOrElse(0))),             // $skip
      limit.map(Limit(_)),                                            // $limit
      idGroup.map(id => Group(id)(groups.get: _*))                    // $group
    ).flatten

    val result = collection.aggregate(params.head, params.tail, false, false)
    result.map(_.documents)

    // TODO: once "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0-play24" is release use the following aggregate call, which uses cursor and should be more optimal
//    val cursor = AggCursor(batchSize = 1)
//
//    val result = collection.aggregate1[JsObject](params.head, params.tail, cursor)
//    result.flatMap(_.collect[List]())
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
    rootCriteria: Seq[Criterion[Any]],
    subCriteria: Seq[Criterion[Any]],
    sort: Seq[Sort],
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