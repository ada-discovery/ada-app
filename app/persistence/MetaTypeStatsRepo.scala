package persistence

import javax.inject.{Inject}

import models.MetaTypeStats
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json._

// trait MetaTypeStatsRepo extends CrudRepo[MetaTypeStats, BSONObjectID]

protected class MetaTypeStatsMongoCrudRepo(collectionName : String) extends EntityMongoCrudRepo[MetaTypeStats, BSONObjectID](collectionName)// with MetaTypeStatsRepo