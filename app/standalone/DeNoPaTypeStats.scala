package standalone

import javax.inject.Inject

import models.MetaTypeStats
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration._
import persistence.{AsyncReadonlyRepo, CrudRepo, DeNoPaBaselineMetaTypeStatsRepo, DeNoPaFirstVisitMetaTypeStatsRepo}
import scala.concurrent.Await
import scala.io.Source

class DeNoPaPaTypeStats @Inject() (
    baselineStatsRepo : DeNoPaBaselineMetaTypeStatsRepo,
    firstVisitStatsRepo : DeNoPaFirstVisitMetaTypeStatsRepo
  ) extends Runnable {

  val timeout = 120000 millis

  val truthValues = List("na", "ja", "nein", "falsch", "richtig")
  val enumValuesThreshold = 20
  val enumFrequencyThreshold = 0.02

  class GlobalMetaTypeCounts(
    var nullOrNaCount : Int = 0,
    var booleanCount : Int = 0,
    var numberEnumCount : Int = 0,
    var freeNumberCount : Int = 0,
    var textEnumCount : Int = 0,
    var freeTextCount : Int = 0
  ) {
    override def toString() : String = s"null-or-NAs : $nullOrNaCount\nbooleans : $booleanCount\nnumber enums: $numberEnumCount\nfree numbers : $freeNumberCount\ntext enums : $textEnumCount\nfree texts : $freeTextCount"
  }

  override def run = {
    val baselineTypeCounts = collectGlobalTypeStats(baselineStatsRepo)
    val firstVisitTypeCounts = collectGlobalTypeStats(firstVisitStatsRepo)

    println("Base line")
    println("---------")
    println(baselineTypeCounts.toString())
    println
    println("First visit")
    println("-----------")
    println(firstVisitTypeCounts.toString())
  }

  private def collectGlobalTypeStats(repo : AsyncReadonlyRepo[MetaTypeStats, BSONObjectID]) = {
    val statsFuture = repo.find(None, Some(Json.obj("attributeName" -> 1)))
    val globalCounts = new GlobalMetaTypeCounts

    Await.result(statsFuture, timeout).foreach{ item =>
      val valueFreqsWoNa = item.valueRatioMap.filterNot(_._1.toLowerCase.equals("na"))
      val valuesWoNa = valueFreqsWoNa.keySet
      val freqsWoNa = valueFreqsWoNa.values.toSeq

      if (isNullOrNA(valuesWoNa))
        globalCounts.nullOrNaCount += 1
      else if (isBoolean(valuesWoNa))
        globalCounts.booleanCount += 1
      else if (isNumberEnum(valuesWoNa, freqsWoNa))
        globalCounts.numberEnumCount += 1
      else if (isNumber(valuesWoNa))
        globalCounts.freeNumberCount += 1
      else if (isTextEnum(valuesWoNa, freqsWoNa))
        globalCounts.textEnumCount += 1
      else
        globalCounts.freeTextCount += 1
    }

    globalCounts
  }

  private def isNullOrNA(valuesWoNA : Set[String]) =
    valuesWoNA.size == 0

  private def isNumber(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => s.forall(c => c.isDigit || c == '.'))

  private def isBoolean(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => truthValues.contains(s.toLowerCase)) || (isNumber(valuesWoNA) && valuesWoNA.size <= 2)

  private def isEnum(freqsWoNa : Seq[Double]) =
    freqsWoNa.size < enumValuesThreshold && (freqsWoNa.sum / freqsWoNa.size) > enumFrequencyThreshold

  private def isNumberEnum(valuesWoNA : Set[String], freqsWoNa : Seq[Double]) =
    isEnum(freqsWoNa) && isNumber(valuesWoNA)

  private def isTextEnum(valuesWoNA : Set[String], freqsWoNa : Seq[Double]) =
    isEnum(freqsWoNa) && valuesWoNA.exists(_.exists(_.isLetter))
}

object DeNoPaPaTypeStats extends GuiceBuilderRunnable[DeNoPaPaTypeStats] with App {
  override def main(args: Array[String]) = run
}