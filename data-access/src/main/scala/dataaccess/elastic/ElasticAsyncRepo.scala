package dataaccess.elastic

import com.sksamuel.elastic4s._
import org.elasticsearch.search.sort.SortOrder
import reactivemongo.bson.BSONObjectID
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Await.result
import java.util.Date

import dataaccess._

abstract protected class ElasticAsyncReadonlyRepo[E, ID](
    indexName: String,
    typeName: String,
    val client: ElasticClient,
    setting: ElasticSetting
  ) extends AsyncReadonlyRepo[E, ID]
      with ElasticSerializer[E]
      with ElasticDsl {

  protected val indexAndType = IndexAndType(indexName, typeName)

  protected val unboundLimit = Integer.MAX_VALUE

  def get(id: ID): Future[Option[E]] =
    client execute {
      ElasticDsl.get id id from indexAndType
    } map (serializeGetResult)

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
          (_: SearchDefinition) bool must (criteria.map(toQuery)) fetchSource(projection.isEmpty)
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

    client execute {
      searchDefinition
    } map { searchResult =>
      val serializationStart = new Date()
      if (searchResult.shardFailures.nonEmpty) {
        throw new AdaDataAccessException(searchResult.shardFailures(0).reason())
      }
      val result: Traversable[E] = projection match {
        case Nil => serializeSearchResult(searchResult)
        case _ => serializeProjectionSearchResults(projectionSeq, searchResult.hits)
      }
      println(s"Serialization for the projection '${projection.mkString(", ")}' finished in ${new Date().getTime - serializationStart.getTime} ms.")
      result
    }
  }

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
    ElasticIdRenameUtil.rename(fieldName, true)

  override def count(criteria: Seq[Criterion[Any]]): Future[Int] = {
    val countDef =
      ElasticDsl.count from indexAndType query new BoolQueryDefinition().must(criteria.map(toQuery))

    client.execute {
      countDef
    }.map(_.getCount.toInt)
  }

  protected def createIndex(): Future[_] =
    client execute {
      create index indexName replicas 0 indexSetting("max_result_window", unboundLimit)
    }

  protected def existsIndex(): Future[Boolean] =
    client execute {
      admin IndexExistsDefinition (Seq(indexName))
    } map (_.isExists)

  protected def createIndexIfNeeded(): Unit =
    result(
      {
        for {
          exists <- existsIndex()
          _ <- if (!exists) createIndex() else Future(())
        } yield
          ()
      },
      20 seconds
    )
}

abstract protected class ElasticAsyncRepo[E, ID](
    indexName: String,
    typeName: String,
    client: ElasticClient,
    setting: ElasticSetting)(
    implicit identity: Identity[E, ID]
  ) extends ElasticAsyncReadonlyRepo[E, ID](indexName, typeName, client, setting) with AsyncRepo[E, ID] {

  protected def flushIndex: Future[Unit] =
    client execute {
      flush index indexName
    } map ( _ => ())

  override def save(entity: E): Future[ID] = {
    val (saveDef, id) = createSaveDefWithId(entity)

    client execute (saveDef refresh setting.saveRefresh) map (_ => id)
  }

  override def save(entities: Traversable[E]): Future[Traversable[ID]] = {
    val saveDefAndIds = entities map createSaveDefWithId

    if (saveDefAndIds.nonEmpty) {
      client execute {
        bulk {
          saveDefAndIds.toSeq map (_._1)
        } refresh setting.saveBulkRefresh
      } map (_ =>
        saveDefAndIds map (_._2)
      )
    } else
      Future(Nil)
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

abstract protected class ElasticAsyncCrudRepo[E, ID](
    indexName: String,
    typeName: String,
    client: ElasticClient,
    setting: ElasticSetting = ElasticSetting())(
    implicit identity: Identity[E, ID]
  ) extends ElasticAsyncRepo[E, ID](indexName, typeName, client, setting) with AsyncCrudRepo[E, ID] {

  override def update(entity: E): Future[ID] = {
    val (updateDef, id) = createUpdateDefWithId(entity)

    client execute (updateDef refresh setting.updateRefresh) map (_ => id)
  }

  override def update(entities: Traversable[E]): Future[Traversable[ID]] = {
    val updateDefAndIds = entities map createUpdateDefWithId

    if (updateDefAndIds.nonEmpty) {
      client execute {
        bulk {
          updateDefAndIds.toSeq map (_._1)
        } refresh setting.updateBulkRefresh
      } map (_ =>
        updateDefAndIds map (_._2)
      )
    } else
      Future(Nil)
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