package runnables.denopa

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date
import javax.inject.{Inject, Named}

import models.MetaTypeStats
import persistence.RepoTypeRegistry._
import persistence._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.DeNoPaSetting._
import runnables.{CleanupDataSet, GuiceBuilderRunnable}

import scala.concurrent.Await
import scala.concurrent.duration._

class CleanupDeNoPaBaseline @Inject()(
    @Named("DeNoPaBaselineRepo") baselineRepo: JsObjectCrudRepo,
    @Named("DeNoPaCuratedBaselineRepo") curatedBaselineRepo : JsObjectCrudRepo,
    @Named("DeNoPaBaselineDictionaryRepo") baselineDictionaryRepo : DictionaryFieldRepo,
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(baselineRepo, curatedBaselineRepo, baselineDictionaryRepo, translationRepo)

class CleanupDeNoPaFirstVisit @Inject()(
    @Named("DeNoPaFirstVisitRepo") firstVisitRepo: JsObjectCrudRepo,
    @Named("DeNoPaCuratedFirstVisitRepo") curatedFirstVisitRepo: JsObjectCrudRepo,
    @Named("DeNoPaFirstVisitDictionaryRepo") firstVisitDictionaryRepo : DictionaryFieldRepo,
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(firstVisitRepo, curatedFirstVisitRepo, firstVisitDictionaryRepo, translationRepo)

object CleanupDeNoPaBaseline extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }
object CleanupDeNoPaFirstVisit extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }