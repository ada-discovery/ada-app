package persistence

import play.api.libs.json.{Json, JsObject}
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json._
import persistence.RepoTypeRegistry.JsObjectCrudRepo
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

protected class JsObjectMongoCrudRepo(
    collectionName : String,
    identityName : String = "_id"
  ) extends MongoAsyncReadonlyRepo[JsObject, BSONObjectID](collectionName, identityName) with JsObjectCrudRepo {

  override def save(entity: JsObject): Future[Either[String, BSONObjectID]] = {
    val id = BSONObjectID.generate
    entity ++ Json.obj(identityName -> id)
    collection.insert(entity).map {
      case le if le.ok == true => Right(id)
      case le => Left(le.message)
    }
  }

  override def update(entity: JsObject): Future[Either[String, BSONObjectID]] = {
    val id = (entity \ identityName).as[BSONObjectID]
    collection.update(Json.obj(identityName -> id), entity) map {
      case le if le.ok == true => Right(id)
      case le => Left(le.message)
    }
  }

  override def delete(id: BSONObjectID): Future[Either[String, BSONObjectID]] = {
    collection.remove(Json.obj(identityName -> id)) map {  // collection.remove(Json.obj(identity.name -> id), firstMatchOnly = true)
      case le if le.ok == true => Right(id)
      case le => Left(le.message)
    }
  }

  override def deleteAll : Future[String] = {
    collection.remove(Json.obj()).map {
      case le if le.ok == true => "ok"
      case le => le.message
    }
  }
}
