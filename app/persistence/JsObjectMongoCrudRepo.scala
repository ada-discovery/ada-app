package persistence

import com.google.inject.assistedinject.Assisted
import play.api.libs.json.{Json, JsObject}
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json._
import persistence.RepoTypes.JsObjectCrudRepo
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.json.BSONFormats.BSONObjectIDFormat
import scala.concurrent.Future
import javax.inject.Inject

protected class JsObjectMongoCrudRepo @Inject() (
    @Assisted collectionName : String
  ) extends MongoAsyncReadonlyRepo[JsObject, BSONObjectID](collectionName, "_id") with JsObjectCrudRepo {

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

trait JsObjectCrudRepoFactory {
  def apply(collectionName: String): JsObjectCrudRepo
}