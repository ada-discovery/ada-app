package persistence

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import models.Translation
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._

@ImplementedBy(classOf[TranslationMongoCrudRepo])
trait TranslationRepo extends CrudRepo[Translation, BSONObjectID]

import play.modules.reactivemongo.json.collection._

@Singleton
class TranslationMongoCrudRepo @Inject() (
    val reactiveMongoApi: ReactiveMongoApi) extends MongoCrudRepo[Translation, BSONObjectID] with TranslationRepo {

  override val collection: JSONCollection = reactiveMongoApi.db.collection("translations")
}