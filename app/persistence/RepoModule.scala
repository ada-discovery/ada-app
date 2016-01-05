package persistence

import models._
import net.codingwell.scalaguice.ScalaModule
import persistence.RepoTypeRegistry._
import com.google.inject.name.Names
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._
import persistence.RepoDef.Repo

object RepoTypeRegistry {
  type JsObjectCrudRepo = AsyncCrudRepo[JsObject, BSONObjectID]
  type MetaTypeStatsRepo = AsyncCrudRepo[MetaTypeStats, BSONObjectID]
  type TranslationRepo = AsyncCrudRepo[Translation, BSONObjectID]
  type UserRepo = AsyncCrudRepo[User, BSONObjectID]
  type MessageRepo = AsyncStreamRepo[Message, BSONObjectID]

  type DictionaryRootRepo = AsyncCrudRepo[Dictionary, BSONObjectID]
}

object RepoDef extends Enumeration {
  case class Repo[T : Manifest](repo : T, named : Boolean = false) extends super.Val
  implicit def valueToRepo[T](x: Value) = x.asInstanceOf[Repo[T]]

  private val DeNoPaBaselineRepos = crateDataAndDictionaryRepos("denopa-baseline_visit")
  private val DeNoFirstVisitRepos = crateDataAndDictionaryRepos("denopa-first_visit")
  private val DeNoPaCuratedBaselineRepos = crateDataAndDictionaryRepos("denopa-baseline_visit-curated")
  private val DeNoPaCuratedFirstVisitRepos = crateDataAndDictionaryRepos("denopa-first_visit-curated")
  private val LuxParkRepos = crateDataAndDictionaryRepos("luxpark")

  val DeNoPaBaselineRepo = DeNoPaBaselineRepos._1
  val DeNoPaBaselineDictionaryRepo = DeNoPaBaselineRepos._2

  val DeNoPaFirstVisitRepo = DeNoFirstVisitRepos._1
  val DeNoPaFirstVisitDictionaryRepo = DeNoFirstVisitRepos._2

  val DeNoPaCuratedBaselineRepo = DeNoPaCuratedBaselineRepos._1
  val DeNoPaCuratedBaselineDictionaryRepo = DeNoPaCuratedBaselineRepos._2

  val DeNoPaCuratedFirstVisitRepo = DeNoPaCuratedFirstVisitRepos._1
  val DeNoPaCuratedFirstVisitDictionaryRepo = DeNoPaCuratedFirstVisitRepos._2

  val LuxParkRepo = LuxParkRepos._1
  val LuxParkDictionaryRepo = LuxParkRepos._2

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

  val DictionaryRootRepo = Repo[DictionaryRootRepo](
    new MongoAsyncCrudRepo[Dictionary, BSONObjectID]("dictionaries"))

  private def crateDataAndDictionaryRepos(dataCollectionName : String) = {
    val dataRepo = Repo[JsObjectCrudRepo](
      new JsObjectMongoCrudRepo(dataCollectionName), true)

    val dictionaryRepo = Repo[DictionaryFieldRepo](
      new DictionaryFieldMongoAsyncCrudRepo(dataCollectionName, dataRepo.repo), true)

    (dataRepo, dictionaryRepo)
  }
}

// repo module used to bind repo types/instances withing Guice IoC container
class RepoModule extends ScalaModule {

  def configure = {
    // TODO: fix manifest erasure to call:
    // RepoDef.values.foreach {r => bindRepo(r)}

    bindRepo(RepoDef.DeNoPaBaselineRepo)
    bindRepo(RepoDef.DeNoPaBaselineDictionaryRepo)

    bindRepo(RepoDef.DeNoPaFirstVisitRepo)
    bindRepo(RepoDef.DeNoPaFirstVisitDictionaryRepo)

    bindRepo(RepoDef.DeNoPaCuratedBaselineRepo)
    bindRepo(RepoDef.DeNoPaCuratedBaselineDictionaryRepo)

    bindRepo(RepoDef.DeNoPaCuratedFirstVisitRepo)
    bindRepo(RepoDef.DeNoPaCuratedFirstVisitDictionaryRepo)

    bindRepo(RepoDef.DeNoPaBaselineMetaTypeStatsRepo)
    bindRepo(RepoDef.DeNoPaFirstVisitMetaTypeStatsRepo)

    bindRepo(RepoDef.LuxParkRepo)
    bindRepo(RepoDef.LuxParkDictionaryRepo)

    bindRepo(RepoDef.TranslationRepo)
    bindRepo(RepoDef.UserRepo)
    bindRepo(RepoDef.MessageRepo)

    bindRepo(RepoDef.DictionaryRootRepo)
  }

  private def bindRepo[T : Manifest](repo : Repo[T]) =
    if (repo.named)
      bind[T]
        .annotatedWith(Names.named(repo.toString))
        .toInstance(repo.repo)
    else
      bind[T].toInstance(repo.repo)
}