package persistence

import models.{Message, User, Translation, MetaTypeStats}
import net.codingwell.scalaguice.ScalaModule
import persistence.RepoTypeRegistry._
import com.google.inject.name.Names
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._
import scala.tools.nsc.doc.model.Val
import persistence.RepoDef.Repo

object RepoTypeRegistry {
  type JsObjectCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]
  type MetaTypeStatsRepo = AsyncCrudRepo[MetaTypeStats, BSONObjectID]
  type TranslationRepo = AsyncCrudRepo[Translation, BSONObjectID]
  type UserRepo = AsyncCrudRepo[User, BSONObjectID]
  type MessageRepo = AsyncStreamRepo[Message, BSONObjectID]
}

object RepoDef extends Enumeration {
  case class Repo[T : Manifest](repo : T, named : Boolean = false) extends super.Val
  implicit def valueToRepo[T](x: Value) = x.asInstanceOf[Repo[T]]

  val DeNoPaBaselineRepo = Repo[JsObjectCrudRepo](
    new JsObjectMongoCrudRepo("denopa-baseline_visit"), true)

  val DeNoPaFirstVisitRepo = Repo[JsObjectCrudRepo](
    new JsObjectMongoCrudRepo("denopa-first_visit"), true)

  val DeNoPaCuratedBaselineRepo = Repo[JsObjectCrudRepo](
    new JsObjectMongoCrudRepo("denopa-baseline_visit-curated"), true)

  val DeNoPaCuratedFirstVisitRepo = Repo[JsObjectCrudRepo](
    new JsObjectMongoCrudRepo("denopa-first_visit-curated"), true)

  val DeNoPaBaselineMetaTypeStatsRepo = Repo[MetaTypeStatsRepo](
    new MongoAsyncCrudRepo[MetaTypeStats, BSONObjectID]("denopa-baseline_visit-metatype_stats"), true)

  val DeNoPaFirstVisitMetaTypeStatsRepo = Repo[MetaTypeStatsRepo](
    new MongoAsyncCrudRepo[MetaTypeStats, BSONObjectID]("denopa-first_visit-metatype_stats"), true)

  val TranslationRepo = Repo[TranslationRepo](
    new MongoAsyncCrudRepo[Translation, BSONObjectID]("translations"))

  val UserRepo = Repo[UserRepo](
    new MongoAsyncCrudRepo[User, BSONObjectID]("users"))

  val MessageRepo = Repo[MessageRepo](
    new MongoAsyncStreamRepo[Message, BSONObjectID]("messages"))
}


// repo module used to bind repo types/instances withing Guice IoC container
class RepoModule extends ScalaModule {

  def configure = {
    // TODO: fix manifest erasure to call:
    // RepoDef.values.foreach {r => bindRepo(r)}

    bindRepo(RepoDef.DeNoPaBaselineRepo)
    bindRepo(RepoDef.DeNoPaFirstVisitRepo)
    bindRepo(RepoDef.DeNoPaCuratedBaselineRepo)
    bindRepo(RepoDef.DeNoPaCuratedFirstVisitRepo)
    bindRepo(RepoDef.DeNoPaBaselineMetaTypeStatsRepo)
    bindRepo(RepoDef.DeNoPaFirstVisitMetaTypeStatsRepo)
    bindRepo(RepoDef.TranslationRepo)
    bindRepo(RepoDef.UserRepo)
    bindRepo(RepoDef.MessageRepo)
  }

  private def bindRepo[T : Manifest](repo : Repo[T]) =
    if (repo.named)
      bind[T]
        .annotatedWith(Names.named(repo.toString))
        .toInstance(repo.repo)
    else
      bind[T].toInstance(repo.repo)
}