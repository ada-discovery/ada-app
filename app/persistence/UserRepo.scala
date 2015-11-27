package persistence

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import models.User
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._

@ImplementedBy(classOf[UserMongoCrudRepo])
trait UserRepo extends CrudRepo[User, BSONObjectID]

@Singleton
class UserMongoCrudRepo extends EntityMongoCrudRepo[User, BSONObjectID]("users") with UserRepo