package standalone

import javax.inject.Inject

import models.MetaTypeStats
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration._
import persistence.{AsyncReadonlyRepo, CrudRepo, DeNoPaBaselineMetaTypeStatsRepo, DeNoPaFirstVisitMetaTypeStatsRepo}
import scala.concurrent.Await

class DeNoPaPlayground @Inject() (
    baselineStatsRepo : DeNoPaBaselineMetaTypeStatsRepo,
    firstVisitStatsRepo : DeNoPaFirstVisitMetaTypeStatsRepo
  ) extends Runnable {

  val timeout = 120000 millis

  val truthValues = List("na", "ja", "nein", "falsch", "richtig", "fehlend")

  override def run = {
    val baselineTexts = collectTexts(baselineStatsRepo)
    val firstVisitTexts = collectTexts(firstVisitStatsRepo)
    val mergedTexts = (baselineTexts ++ firstVisitTexts).toSet.toSeq.sorted
    println(mergedTexts.size)
  }

  private def collectTexts(repo : AsyncReadonlyRepo[MetaTypeStats, BSONObjectID]) = {
    val statsFuture = repo.find(None, Some(Json.obj("attributeName" -> 1)))
    val enumFields = Await.result(statsFuture, timeout).filter{ item =>
      val keys = item.valueRatioMap.keySet
      (keys.size > 1 && keys.size < 20 && keys.filterNot(_.equals("NA")).exists(_.exists(_.isLetter) && !keys.forall(s => truthValues.contains(s.toLowerCase))))
    }
    enumFields.map { field =>
      //      println(s"${field.attributeName}§${field.valueRatioMap.keySet.toSeq.filterNot(_.equals("NA")).sorted.mkString("§")}")
      field.valueRatioMap.keySet.toSeq.filter(s => !s.equals("NA") && s.exists(_.isLetter)).sorted
    }.flatten.toSet.toSeq.sorted
  }
}

object DeNoPaPlayground extends GuiceBuilderRunnable[DeNoPaPlayground] with App {
  override def main(args: Array[String]) = run
}