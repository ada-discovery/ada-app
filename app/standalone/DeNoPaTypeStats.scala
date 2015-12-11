package standalone

import javax.inject.{Named, Inject}

import models.MetaTypeStats
import persistence.RepoTypeRegistry._
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import services.DeNoPaSetting._

import scala.concurrent.duration._
import persistence.{AsyncCrudRepo, AsyncReadonlyRepo}
import scala.concurrent.Await

class DeNoPaTypeStats @Inject() (
    @Named("DeNoPaBaselineMetaTypeStatsRepo") baselineStatsRepo : MetaTypeStatsRepo,
    @Named("DeNoPaFirstVisitMetaTypeStatsRepo") firstVisitStatsRepo : MetaTypeStatsRepo
  ) extends Runnable {

  val timeout = 120000 millis

  override def run = {
    val baselineTypeCounts = collectBaselineGlobalTypeStats
    val firstVisitTypeCounts = collectFirstVisitGlobalTypeStats

    println("Base line")
    println("---------")
    println(baselineTypeCounts.toString())
    println
    println("First visit")
    println("-----------")
    println(firstVisitTypeCounts.toString())
  }

  def collectBaselineGlobalTypeStats = collectGlobalTypeStats(baselineStatsRepo)

  def collectFirstVisitGlobalTypeStats = collectGlobalTypeStats(firstVisitStatsRepo)

  private def collectGlobalTypeStats(repo : AsyncReadonlyRepo[MetaTypeStats, BSONObjectID]) = {
    val statsFuture = repo.find(None, Some(Json.obj("attributeName" -> 1)))
    val globalCounts = new TypeCount

    Await.result(statsFuture, timeout).foreach{ item =>
      val valueFreqsWoNa = item.valueRatioMap.filterNot(_._1.toLowerCase.equals("na"))
      val valuesWoNa = valueFreqsWoNa.keySet
      val freqsWoNa = valueFreqsWoNa.values.toSeq

      if (typeInferenceProvider.isNull(valuesWoNa))
        globalCounts.nullOrNa += 1
      else if (typeInferenceProvider.isBoolean(valuesWoNa))
        globalCounts.boolean += 1
      else if (typeInferenceProvider.isDate(valuesWoNa))
        globalCounts.date += 1
      else if (typeInferenceProvider.isNumberEnum(valuesWoNa, freqsWoNa))
        globalCounts.numberEnum += 1
      else if (typeInferenceProvider.isNumber(valuesWoNa))
        globalCounts.freeNumber += 1
      else if (typeInferenceProvider.isTextEnum(valuesWoNa, freqsWoNa))
        globalCounts.textEnum += 1
      else
        globalCounts.freeText += 1
    }

    globalCounts
  }
}

object DeNoPaTypeStats extends GuiceBuilderRunnable[DeNoPaTypeStats] with App {
  run
}