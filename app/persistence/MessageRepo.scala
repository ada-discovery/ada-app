package persistence

import javax.inject.{ Named, Inject, Singleton }

import com.google.inject.ImplementedBy
import models.Message
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._

@ImplementedBy(classOf[MessageMongoRepo])
trait MessageRepo extends StreamRepo[Message, BSONObjectID]

import play.modules.reactivemongo.json.collection._

@Singleton
class MessageMongoRepo @Inject() (
    val reactiveMongoApi: ReactiveMongoApi) extends MongoStreamRepo[Message, BSONObjectID] with MessageRepo {

  override val collection: JSONCollection = reactiveMongoApi.db.collection("messages")
}