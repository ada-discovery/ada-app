package dataaccess.elastic

import javax.inject.Inject

import com.evojam.play.elastic4s.configuration.ClusterSetup
import com.evojam.play.elastic4s.{PlayElasticFactory, PlayElasticJsonSupport}
import com.sksamuel.elastic4s._
import org.elasticsearch.search.sort.SortOrder
import reactivemongo.bson.BSONObjectID
import scala.concurrent.duration._
import play.api.libs.json.Format

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Await.result
import java.util.Date

import dataaccess._

abstract protected class ElasticAsyncReadonlyRepo[E, ID](
    indexName: String,
    typeName: String
  ) extends ElasticDsl with PlayElasticJsonSupport with AsyncReadonlyRepo[E, ID] {

  @Inject protected var cs: ClusterSetup = _
  @Inject protected var elasticFactory: PlayElasticFactory = _

  protected val indexAndType = IndexAndType(indexName, typeName)
  protected lazy val client: ElasticClient = result(
    {
      println(s"Loading Elastic '$indexName', instance: ${this.toString}")
      val e = elasticFactory(cs)
      // create index if needed
      for {
        exists <- existsIndex(e)
        _ <- if (!exists) createIndex(e) else Future(())
      } yield
        e
    },
    10 seconds
  )

  protected val unboundLimit = Integer.MAX_VALUE

  def get(id: ID): Future[Option[E]] =
    client execute {
      ElasticDsl.get id id from indexAndType
    } map (serializeGetResult)

  protected def serializeGetResult(response: RichGetResponse): Option[E]

  override def find(
    criteria: Seq[Criterion[Any]],
    sort: Seq[Sort],
    projection: Traversable[String],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[Traversable[E]] = {
    val projectionSeq = projection.map(toDBFieldName).toSeq

    val searchDefs: Seq[(Boolean, SearchDefinition => SearchDefinition)] =
      Seq(
        // criteria
        (
          criteria.nonEmpty,
          (_: SearchDefinition) bool must (criteria.map(toQuery))
        ),

        // projection
        (
          projection.nonEmpty,
          (_: SearchDefinition) fields (projectionSeq: _*)
        ),

        // sort
        (
          sort.nonEmpty,
          (_: SearchDefinition) sort (toSort(sort): _*)
        ),

        // start and skip
        (
          true,
          if (limit.isDefined)
            (_: SearchDefinition) start skip.getOrElse(0) limit limit.get
          else
            // if undefined we still need to pass "unbound" limit, since by default ES returns only 10 items
            (_: SearchDefinition) limit unboundLimit
        )
      )

    val searchDefinition = searchDefs.foldLeft(search in indexAndType) {
      case (sd, (cond, createNewDef)) =>
        if (cond) createNewDef(sd) else sd
    }

    def serializeProjectionSearchResult(result: Seq[RichSearchHitField]): E = {
      val nameValues = result.map( field => (field.name, field.getValue[Any]))
      serializeSearchResult(nameValues)
    }

    client execute {
      searchDefinition
    } map { searchResult =>
      val serializationStart = new Date()
      if (searchResult.shardFailures.nonEmpty) {
        throw new AdaDataAccessException(searchResult.shardFailures(0).reason())
      }
      val result: Traversable[E] = projection match {
        case Nil => serializeSearchResult(searchResult)
        case _ => searchResult.hits.map(hit => serializeProjectionSearchResult(hit.fieldsSeq))
      }
      println(s"Serialization for the projection '${projection.mkString(", ")}' finished in ${new Date().getTime - serializationStart.getTime} ms.")
      result
    }
  }

  protected def serializeSearchResult(response: RichSearchResponse): Traversable[E]

  protected def serializeSearchResult(result: Traversable[(String, Any)]): E

  private def toSort(sorts: Seq[Sort]): Seq[SortDefinition] =
    sorts map {
      _ match {
        case AscSort(fieldName) => FieldSortDefinition(toDBFieldName(fieldName)) order SortOrder.ASC
        case DescSort(fieldName) => FieldSortDefinition(toDBFieldName(fieldName)) order SortOrder.DESC
      }
    }

  protected def toQuery[T, V](criterion: Criterion[T]): QueryDefinition = {
    val fieldName = toDBFieldName(criterion.fieldName)

    val qDef = criterion match {
      case c: EqualsCriterion[T] =>
        TermQueryDefinition(fieldName, toDBValue(c.value))

      case c: EqualsNullCriterion =>
        new BoolQueryDefinition().not(ExistsQueryDefinition(fieldName))

      case c: RegexEqualsCriterion =>
        RegexQueryDefinition(fieldName, c.value)

      case c: NotEqualsCriterion[T] =>
        new BoolQueryDefinition().not(TermQueryDefinition(fieldName, toDBValue(c.value)))

      case c: NotEqualsNullCriterion =>
        ExistsQueryDefinition(fieldName)

      case c: InCriterion[V] =>
        TermsQueryDefinition(fieldName, c.value.map(value => toDBValue(value).toString))

      case c: NotInCriterion[V] =>
        new BoolQueryDefinition().not(TermsQueryDefinition(fieldName, c.value.map(_.toString)))

      case c: GreaterCriterion[T] =>
        RangeQueryDefinition(fieldName) from toDBValue(c.value) includeLower false

      case c: GreaterEqualCriterion[T] =>
        RangeQueryDefinition(fieldName) from toDBValue(c.value) includeLower true

      case c: LessCriterion[T] =>
        RangeQueryDefinition(fieldName) to toDBValue(c.value) includeUpper false

      case c: LessEqualCriterion[T] =>
        RangeQueryDefinition(fieldName) to toDBValue(c.value) includeUpper true
    }

    if (fieldName.contains(".")) {
      val path = fieldName.takeWhile(!_.equals('.'))
      println("Path: " + path)
      NestedQueryDefinition(path, qDef)
    } else
      qDef
  }

  private def toDBValue(value: Any): Any =
    value match {
      case e: Date => e.getTime
      case b: BSONObjectID => b.stringify
      case _ => value
    }

  private def toDBFieldName(fieldName: String): String =
   fieldName match {
      case JsonIdRenameFormat.originalIdName => JsonIdRenameFormat.newIdName + ".$oid"
      case _ => fieldName
    }

  override def count(criteria: Seq[Criterion[Any]]): Future[Int] = {
    def countDef =
      ElasticDsl.count from indexAndType query new BoolQueryDefinition().must(criteria.map(toQuery))

    client.execute {
      countDef
    }.map(_.getCount.toInt)
  }

  protected def createIndex(c: ElasticClient = client) =
    c execute {
      create index indexName replicas 0
    }

  protected def existsIndex(c: ElasticClient = client): Future[Boolean] =
    c execute {
      admin IndexExistsDefinition(Seq(indexName))
    } map (_.isExists)
}

class ElasticFormatAsyncReadonlyRepo[E, ID](
    indexName: String,
    typeName: String)(
    implicit format: Format[E], manifest: Manifest[E]
  ) extends ElasticAsyncReadonlyRepo[E, ID](indexName, typeName) {

  override protected def serializeGetResult(response: RichGetResponse) =
    response.as[E]

  override protected def serializeSearchResult(response: RichSearchResponse) =
    response.as[E]

  override protected def serializeSearchResult(result: Traversable[(String, Any)]): E = ???
}

abstract protected class ElasticAsyncRepo[E, ID](
    indexName: String,
    typeName: String)(
    implicit identity: Identity[E, ID]
  ) extends ElasticAsyncReadonlyRepo[E, ID](indexName, typeName) with AsyncRepo[E, ID] {

  override def save(entity: E): Future[ID] = {
    val (saveDef, id) = createSaveDefWithId(entity)

    client execute saveDef map (_ => id)
  }

  override def save(entities: Traversable[E]): Future[Traversable[ID]] = {
    val saveDefAndIds = entities map createSaveDefWithId

    client execute {
      bulk {
        saveDefAndIds.toSeq map (_._1)
      }
    } map (_ =>
      saveDefAndIds map (_._2)
    )
  }

  protected def createSaveDefWithId(entity: E): (IndexDefinition, ID) = {
    val (id, entityWithId) = getIdWithEntity(entity)
    (createSaveDef(entityWithId, id), id)
  }

  protected def createSaveDef(entity: E, id: ID): IndexDefinition

  protected def getIdWithEntity(entity: E): (ID, E) =
    identity.of(entity) match {
      case Some(givenId) => (givenId, entity)
      case None => {
        val id = identity.next
        (id, identity.set(entity, id))
      }
    }
}

class ElasticFormatAsyncRepo[E, ID](
    indexName: String,
    typeName: String)(
    implicit format: Format[E], manifest: Manifest[E], identity: Identity[E, ID]
  ) extends ElasticAsyncRepo[E, ID](indexName, typeName) {

  override protected def serializeGetResult(response: RichGetResponse) =
    response.as[E]

  override protected def serializeSearchResult(response: RichSearchResponse) =
    response.as[E]

  override protected def serializeSearchResult(result: Traversable[(String, Any)]): E = ???

  override protected def createSaveDef(entity: E, id: ID): IndexDefinition =
     index into indexAndType source entity id id
}

abstract protected class ElasticAsyncCrudRepo[E, ID](
    indexName: String,
    typeName: String)(
    implicit identity: Identity[E, ID]
  ) extends ElasticAsyncRepo[E, ID](indexName, typeName) with AsyncCrudRepo[E, ID] {

  override def update(entity: E): Future[ID] = {
    val (updateDef, id) = createUpdateDefWithId(entity)

    client execute updateDef map (_ => id)
  }

  override def update(entities: Traversable[E]): Future[Traversable[ID]] = {
    val updateDefAndIds = entities map createUpdateDefWithId

    client execute {
      bulk {
        updateDefAndIds.toSeq map (_._1)
      }
    } map (_ =>
      updateDefAndIds map (_._2)
    )
  }

  protected def createUpdateDefWithId(entity: E): (UpdateDefinition, ID) = {
    val id = identity.of(entity).getOrElse(
      throw new IllegalArgumentException(s"Elastic update method expects an entity with id but '$entity' provided.")
    )
    (createUpdateDef(entity, id), id)
  }

  protected def createUpdateDef(entity: E, id: ID): UpdateDefinition

  override def delete(id: ID): Future[Unit] =
    client execute {
      ElasticDsl.delete id id from indexAndType
    } map (_ => ())

  override def deleteAll: Future[Unit] =
    for {
      indexExists <- existsIndex()
      _ <- if (indexExists)
          client execute {
            ElasticDsl.delete index indexName
          }
        else
          Future(())
      _ <- createIndex()
    } yield
      ()
}

class ElasticFormatAsyncCrudRepo[E, ID](
    indexName: String,
    typeName: String)(
    implicit format: Format[E], manifest: Manifest[E], identity: Identity[E, ID]
  ) extends ElasticAsyncCrudRepo[E, ID](indexName, typeName) {

  override protected def serializeGetResult(response: RichGetResponse) =
    response.as[E]

  override protected def serializeSearchResult(response: RichSearchResponse) =
    response.as[E]

  override protected def serializeSearchResult(result: Traversable[(String, Any)]): E = ???

  override protected def createSaveDef(entity: E, id: ID) =
    index into indexAndType source entity id id

  override def createUpdateDef(entity: E, id: ID) =
    ElasticDsl.update id id in indexAndType source entity
}