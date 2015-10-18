package persistence

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import models.MetaTypeStats
import play.api.libs.json.{Json, JsObject}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.collection._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.json._

import scala.concurrent.Future

// TODO: Simplify and/or move to configuration file

// baseline
@ImplementedBy(classOf[DeNoPaBaselineMongoCrudRepo])
trait DeNoPaBaselineRepo extends CrudRepo[JsObject, BSONObjectID]

class DeNoPaBaselineMongoCrudRepo @Inject()(reactiveMongoApi: ReactiveMongoApi) extends JsObjectMongoCrudRepo(reactiveMongoApi, "denopa-baseline_visit") with DeNoPaBaselineRepo

// firstvisit
@ImplementedBy(classOf[DeNoPaFirstVisitMongoCrudRepo])
trait DeNoPaFirstVisitRepo extends CrudRepo[JsObject, BSONObjectID]

class DeNoPaFirstVisitMongoCrudRepo @Inject()(reactiveMongoApi: ReactiveMongoApi) extends JsObjectMongoCrudRepo(reactiveMongoApi, "denopa-first_visit") with DeNoPaFirstVisitRepo

// baseline
@ImplementedBy(classOf[DeNoPaBaselineMetaTypeStatsMongoCrudRepo])
trait DeNoPaBaselineMetaTypeStatsRepo extends CrudRepo[MetaTypeStats, BSONObjectID]

@Singleton
class DeNoPaBaselineMetaTypeStatsMongoCrudRepo @Inject()(reactiveMongoApi: ReactiveMongoApi) extends MetaTypeStatsMongoCrudRepo(reactiveMongoApi, "denopa-baseline_visit-metatype_stats") with DeNoPaBaselineMetaTypeStatsRepo

// firstvisit
@ImplementedBy(classOf[DeNoPaFirstVisitMetaTypeStatsMongoCrudRepo])
trait DeNoPaFirstVisitMetaTypeStatsRepo extends CrudRepo[MetaTypeStats, BSONObjectID]

@Singleton
class DeNoPaFirstVisitMetaTypeStatsMongoCrudRepo @Inject()(reactiveMongoApi: ReactiveMongoApi) extends MetaTypeStatsMongoCrudRepo(reactiveMongoApi, "denopa-first_visit-metatype_stats") with DeNoPaFirstVisitMetaTypeStatsRepo



class MetaTypeStatsMongoCrudRepo(reactiveMongoApi: ReactiveMongoApi, collectionName : String) extends MongoCrudRepo[MetaTypeStats, BSONObjectID] {
  override val collection: JSONCollection = reactiveMongoApi.db.collection(collectionName)
}
