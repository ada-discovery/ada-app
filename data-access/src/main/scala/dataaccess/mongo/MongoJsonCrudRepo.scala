package dataaccess.mongo

import javax.cache.configuration.Factory

import com.google.inject.assistedinject.Assisted
import models.FieldTypeSpec
import play.api.libs.json.{Json, JsObject}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.modules.reactivemongo.{ReactiveMongoApi, DefaultReactiveMongoApi}
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json._
import dataaccess.RepoTypes.JsonCrudRepo
import dataaccess.{AsyncCrudRepo, SerializableApplicationLifecycle, RepoException}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.json.BSONFormats.BSONObjectIDFormat
import scala.concurrent.Future
import javax.inject.Inject

class MongoJsonCrudRepo @Inject()(
    @Assisted collectionName : String,
    @Assisted fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],
    @Assisted mongoAutoCreateIndexForProjection: Boolean
  ) extends MongoAsyncReadonlyRepo[JsObject, BSONObjectID](collectionName, "_id", mongoAutoCreateIndexForProjection) with JsonCrudRepo {

  override def save(entity: JsObject): Future[BSONObjectID] = {
    val (doc, id) = addId(entity)

    collection.insert(entity).map {
      case le if le.ok => id
      case le => throw new RepoException(le.message)
    }
  }

  override def save(entities: Traversable[JsObject]): Future[Traversable[BSONObjectID]] = {
    val docAndIds = entities.map(addId)

    collection.bulkInsert(docAndIds.map(_._1).toStream, ordered = false).map {
      case le if le.ok => docAndIds.map(_._2)
      case le => throw new RepoException(le.errmsg.getOrElse(""))
    }
  }

  private def addId(entity: JsObject): (JsObject, BSONObjectID) = {
    val id = BSONObjectID.generate
    (entity ++ Json.obj(identityName -> id), id)
  }

  override def update(entity: JsObject): Future[BSONObjectID] = {
    val id = (entity \ identityName).as[BSONObjectID]
    collection.update(Json.obj(identityName -> id), entity) map {
      case le if le.ok => id
      case le => throw new RepoException(le.message)
    }
  }

  override def delete(id: BSONObjectID) =
    collection.remove(Json.obj(identityName -> id)) map handleResult

  override def deleteAll : Future[Unit] =
    collection.remove(Json.obj()) map handleResult
}

class MongoJsonRepoFactory(
    collectionName: String,
    createIndexForProjectionAutomatically: Boolean,
    configuration: Configuration,
    applicationLifecycle: ApplicationLifecycle
  ) extends Factory[AsyncCrudRepo[JsObject, BSONObjectID]] {

  override def create(): AsyncCrudRepo[JsObject, BSONObjectID] = {
    val repo = new MongoJsonCrudRepo(collectionName, Nil, createIndexForProjectionAutomatically)
    repo.reactiveMongoApi = ReactiveMongoApi.create(configuration, applicationLifecycle)
    repo
  }
}

object ReactiveMongoApi {
  private var reactiveMongoApi: Option[DefaultReactiveMongoApi] = None

  def create(configuration: Configuration, applicationLifecycle: ApplicationLifecycle): ReactiveMongoApi = {
    reactiveMongoApi.getOrElse {
      reactiveMongoApi = Some(new DefaultReactiveMongoApi(configuration, applicationLifecycle))
      reactiveMongoApi.get
    }
  }

  def get = reactiveMongoApi
}