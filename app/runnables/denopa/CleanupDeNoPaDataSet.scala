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
    @Named("DeNoPaBaselineMetaTypeStatsRepo") baselineTypeStatsRepo : MetaTypeStatsRepo,
    translationRepo : TranslationRepo
  ) extends CleanupDataSet(baselineRepo, curatedBaselineRepo, baselineTypeStatsRepo, translationRepo)

class CleanupDeNoPaFirstVisit @Inject()(
    @Named("DeNoPaFirstVisitRepo") firstVisitRepo: JsObjectCrudRepo,
    @Named("DeNoPaCuratedFirstVisitRepo") curatedFirstVisitRepo: JsObjectCrudRepo,
    @Named("DeNoPaFirstVisitMetaTypeStatsRepo") firstVisitTypeStatsRepo : MetaTypeStatsRepo,
    translationRepo : TranslationRepo
    ) extends CleanupDataSet(firstVisitRepo, curatedFirstVisitRepo, firstVisitTypeStatsRepo, translationRepo)

object CleanupDeNoPaBaseline extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }
object CleanupDeNoPaFirstVisit extends GuiceBuilderRunnable[CleanupDeNoPaBaseline] with App { run }