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
    @Assisted collectionName : String,
    identityName : String = "_id"
  ) extends MongoAsyncReadonlyRepo[JsObject, BSONObjectID](collectionName, identityName) with JsObjectCrudRepo {

  override def save(entity: JsObject): Future[BSONObjectID] = {
    val id = BSONObjectID.generate
    entity ++ Json.obj(identityName -> id)
    collection.insert(entity).map {
      case le if le.ok => id
      case le => throw new RepoException(le.message)
    }
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