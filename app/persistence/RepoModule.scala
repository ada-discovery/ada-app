package persistence

import models.MetaTypeStats
import persistence.RepoTypeRegistry.MetaTypeStatsRepo
import play.api._
import play.api.inject._
import com.google.inject.{TypeLiteral, Provides, AbstractModule}
import com.google.inject.name.Names
import persistence.{CrudRepo, JsObjectCrudRepo}
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json._

object RepoTypeRegistry {
  type MetaTypeStatsRepo = CrudRepo[MetaTypeStats, BSONObjectID]
}

class RepoModule extends AbstractModule {
    def configure() = {

      bind(classOf[JsObjectCrudRepo])
        .annotatedWith(Names.named("DeNoPaBaselineRepo"))
        .toInstance(new JsObjectMongoCrudRepo("denopa-baseline_visit"))

      bind(classOf[JsObjectCrudRepo])
        .annotatedWith(Names.named("DeNoPaFirstVisitRepo"))
        .toInstance(new JsObjectMongoCrudRepo("denopa-first_visit"))

      bind(classOf[JsObjectCrudRepo])
        .annotatedWith(Names.named("DeNoPaCuratedBaselineRepo"))
        .toInstance(new JsObjectMongoCrudRepo("denopa-baseline_visit-curated"))

      bind(classOf[JsObjectCrudRepo])
        .annotatedWith(Names.named("DeNoPaCuratedFirstVisitRepo"))
        .toInstance(new JsObjectMongoCrudRepo("denopa-first_visit-curated"))

      bind(new TypeLiteral[MetaTypeStatsRepo]{})
        .annotatedWith(Names.named("DeNoPaBaselineMetaTypeStatsRepo"))
        .toInstance(new EntityMongoCrudRepo[MetaTypeStats, BSONObjectID]("denopa-baseline_visit-metatype_stats"))

      bind(new TypeLiteral[MetaTypeStatsRepo]{})
        .annotatedWith(Names.named("DeNoPaFirstVisitMetaTypeStatsRepo"))
        .toInstance(new EntityMongoCrudRepo[MetaTypeStats, BSONObjectID]("denopa-first_visit-metatype_stats"))
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