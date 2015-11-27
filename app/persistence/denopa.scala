package persistence

import javax.inject.{Inject}

import models.MetaTypeStats
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._

trait MetaTypeStatsRepo extends CrudRepo[MetaTypeStats, BSONObjectID]

protected class MetaTypeStatsMongoCrudRepo(collectionName : String) extends MongoCrudRepo[MetaTypeStats, BSONObjectID] with MetaTypeStatsRepo {

  @Inject var reactiveMongoApi : ReactiveMongoApi = _

  override lazy val collection: JSONCollection = reactiveMongoApi.db.collection(collectionName)
}
