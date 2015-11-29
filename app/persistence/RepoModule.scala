package persistence

import models.{Message, User, Translation, MetaTypeStats}
import persistence.RepoTypeRegistry._
import com.google.inject.{TypeLiteral, AbstractModule}
import com.google.inject.name.Names
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json._

object RepoTypeRegistry {
  type JsObjectCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]
  type MetaTypeStatsRepo = AsyncCrudRepo[MetaTypeStats, BSONObjectID]
  type TranslationRepo = AsyncCrudRepo[Translation, BSONObjectID]
  type UserRepo = AsyncCrudRepo[User, BSONObjectID]
  type MessageRepo = AsyncStreamRepo[Message, BSONObjectID]
}

class RepoModule extends AbstractModule {
    def configure() = {

      bind(new TypeLiteral[JsObjectCrudRepo]{})
        .annotatedWith(Names.named("DeNoPaBaselineRepo"))
        .toInstance(new JsObjectMongoCrudRepo("denopa-baseline_visit"))

      bind(new TypeLiteral[JsObjectCrudRepo]{})
        .annotatedWith(Names.named("DeNoPaFirstVisitRepo"))
        .toInstance(new JsObjectMongoCrudRepo("denopa-first_visit"))

      bind(new TypeLiteral[JsObjectCrudRepo]{})
        .annotatedWith(Names.named("DeNoPaCuratedBaselineRepo"))
        .toInstance(new JsObjectMongoCrudRepo("denopa-baseline_visit-curated"))

      bind(new TypeLiteral[JsObjectCrudRepo]{})
        .annotatedWith(Names.named("DeNoPaCuratedFirstVisitRepo"))
        .toInstance(new JsObjectMongoCrudRepo("denopa-first_visit-curated"))

      bind(new TypeLiteral[MetaTypeStatsRepo]{})
        .annotatedWith(Names.named("DeNoPaBaselineMetaTypeStatsRepo"))
        .toInstance(new MongoAsyncCrudRepo[MetaTypeStats, BSONObjectID]("denopa-baseline_visit-metatype_stats"))

      bind(new TypeLiteral[MetaTypeStatsRepo]{})
        .annotatedWith(Names.named("DeNoPaFirstVisitMetaTypeStatsRepo"))
        .toInstance(new MongoAsyncCrudRepo[MetaTypeStats, BSONObjectID]("denopa-first_visit-metatype_stats"))

      bind(new TypeLiteral[TranslationRepo]{})
        .toInstance(new MongoAsyncCrudRepo[Translation, BSONObjectID]("translations"))

      bind(new TypeLiteral[UserRepo]{})
        .toInstance(new MongoAsyncCrudRepo[User, BSONObjectID]("users"))

      bind(new TypeLiteral[MessageRepo]{})
        .toInstance(new MongoAsyncStreamRepo[Message, BSONObjectID]("messages"))
    }
}

//class RepoModule extends Module {
//  override def bindings(environment : Environment, configuration : Configuration) : Seq[Binding[_]] = {
//    seq(
//      bind(classOf[JsObjectCrudRepo])
//        .qualifiedWith("DeNoPaBaselineRepo")
//        .toInstance(new JsObjectMongoCrudRepo("denopa-baseline_visit")),
//
//      bind(classOf[JsObjectCrudRepo])
//        .qualifiedWith(Names.named("DeNoPaFirstVisitRepo"))
//        .toInstance(new JsObjectMongoCrudRepo("denopa-first_visit")),
//
//      bind(classOf[JsObjectCrudRepo])
//        .qualifiedWith(Names.named("DeNoPaCuratedBaselineRepo"))
//        .toInstance(new JsObjectMongoCrudRepo("denopa-baseline_visit-curated")),
//
//      bind(classOf[JsObjectCrudRepo])
//        .qualifiedWith(Names.named("DeNoPaCuratedFirstVisitRepo"))
//        .toInstance(new JsObjectMongoCrudRepo("denopa-first_visit-curated")),
//
//      bind(classOf[MetaTypeStatsRepo])
//        .qualifiedWith(Names.named("DeNoPaBaselineMetaTypeStatsRepo"))
//        .toInstance(new MetaTypeStatsMongoCrudRepo("denopa-baseline_visit-metatype_stats")),
//
//       bind(classOf[MetaTypeStatsRepo])
//        .qualifiedWith(Names.named("DeNoPaFirstVisitMetaTypeStatsRepo"))
//        .toInstance(new MetaTypeStatsMongoCrudRepo("denopa-first_visit-metatype_stats"))
//    )
//  }
//}