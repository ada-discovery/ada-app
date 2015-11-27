package persistence

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import models.Translation
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._

@ImplementedBy(classOf[TranslationMongoCrudRepo])
trait TranslationRepo extends CrudRepo[Translation, BSONObjectID]

@Singleton
class TranslationMongoCrudRepo extends EntityMongoCrudRepo[Translation, BSONObjectID]("translations") with TranslationRepo