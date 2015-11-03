package persistence

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import models.User
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._

@ImplementedBy(classOf[UserMongoCrudRepo])
trait UserRepo extends CrudRepo[User, BSONObjectID]

import play.modules.reactivemongo.json.collection._

@Singleton
class UserMongoCrudRepo @Inject() (
    val reactiveMongoApi: ReactiveMongoApi) extends MongoCrudRepo[User, BSONObjectID] with UserRepo {

  override val collection: JSONCollection = reactiveMongoApi.db.collection("users")
}