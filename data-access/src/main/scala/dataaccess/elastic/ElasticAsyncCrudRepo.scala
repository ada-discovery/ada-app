package dataaccess.elastic

import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl, UpdateDefinition}
import org.incal.core.Identity
import org.incal.core.dataaccess.AsyncCrudRepo
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
  * Abstract CRUD (create, ready, update, delete) repo for handling storage and retrieval of documents in Elastic Search.
  *
  * @param indexName
  * @param typeName
  * @param client
  * @param setting
  * @param identity
  * @tparam E
  * @tparam ID
  *
  * @since 2018
  */
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

  }.recover(
    handleExceptions
  )

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

  }.recover(
    handleExceptions
  )

  protected def createUpdateDefWithId(entity: E): (UpdateDefinition, ID) = {
    val id = identity.of(entity).getOrElse(
      throw new IllegalArgumentException(s"Elastic update method expects an entity with id but '$entity' provided.")
    )
    (createUpdateDef(entity, id), id)
  }

  protected def createUpdateDef(entity: E, id: ID): UpdateDefinition

  override def delete(id: ID): Future[Unit] = {
    client execute {
      ElasticDsl.delete id id from indexAndType
    } map (_ => ())

  }.recover(
    handleExceptions
  )

  override def deleteAll: Future[Unit] = {
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
  }.recover(
    handleExceptions
  )
}