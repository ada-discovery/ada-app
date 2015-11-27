package persistence

import javax.inject.Inject

import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.collection.JSONCollection
import models.Identity

protected class EntityMongoCrudRepo[E: Format, ID: Format](collectionName : String)(implicit identity: Identity[E, ID]) extends MongoCrudRepo[E, ID] {

  @Inject var reactiveMongoApi : ReactiveMongoApi = _

  override lazy val collection: JSONCollection = reactiveMongoApi.db.collection(collectionName)
}