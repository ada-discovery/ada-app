package persistence

import play.api.libs.json.{Json, JsObject}
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json._
import persistence.RepoTypeRegistry.JsObjectCrudRepo
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.json.BSONFormats.BSONObjectIDFormat
import scala.concurrent.Future

protected class JsObjectMongoCrudRepo(
    collectionName : String,
    identityName : String = "_id"
  ) extends MongoAsyncReadonlyRepo[JsObject, BSONObjectID](collectionName, identityName) with JsObjectCrudRepo {

  override def save(entity: JsObject): Future[BSONObjectID] = {
    val id = BSONObjectID.generate
    entity ++ Json.obj(identityName -> id)
    collection.insert(entity).map {
      case le if le.ok == true => id
      case le => throw new IllegalAccessException(le.message)
    }
  }

  override def update(entity: JsObject): Future[String] = {
    val id = (entity \ identityName).as[BSONObjectID]
    collection.update(Json.obj(identityName -> id), entity) map {
      case le if le.ok == true => id.toString
      case le => throw new IllegalAccessException(le.message)
    }
  }

  override def updateCustom(id: BSONObjectID, modifier : JsObject): Future[String] = {
    collection.update(Json.obj(identityName -> id), modifier) map {
      case le if le.ok == true => id.toString
      case le => throw new IllegalAccessException(le.message)
    }
  }

  override def delete(id: BSONObjectID): Future[BSONObjectID] = {
    collection.remove(Json.obj(identityName -> id)) map {  // collection.remove(Json.obj(identity.name -> id), firstMatchOnly = true)
      case le if le.ok == true => id
      case le => throw new IllegalAccessException(le.message)
    }
  }

  override def deleteAll : Future[String] = {
    collection.remove(Json.obj()).map {
      case le if le.ok == true => "ok"
      case le => throw new IllegalAccessException(le.message)
    }
  }
}
