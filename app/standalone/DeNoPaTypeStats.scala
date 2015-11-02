package standalone

import java.text.{ParseException, SimpleDateFormat}
import javax.inject.Inject

import models.MetaTypeStats
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration._
import persistence.{AsyncReadonlyRepo, DeNoPaBaselineMetaTypeStatsRepo, DeNoPaFirstVisitMetaTypeStatsRepo}
import scala.concurrent.Await

class DeNoPaTypeStats @Inject() (
    baselineStatsRepo : DeNoPaBaselineMetaTypeStatsRepo,
    firstVisitStatsRepo : DeNoPaFirstVisitMetaTypeStatsRepo
  ) extends Runnable {

  val timeout = 120000 millis

  val textBooleanValues = List("ja", "nein", "falsch", "richtig")
  val numBooleanValues = List("0", "1")
  val dateFormats = List("yyyy-MM-dd", "dd.MM.yyyy", "MM.yyyy")

  val enumValuesThreshold = 20
  val enumFrequencyThreshold = 0.02

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

      if (isNullOrNA(valuesWoNa))
        globalCounts.nullOrNa += 1
      else if (isBoolean(valuesWoNa))
        globalCounts.boolean += 1
      else if (isDate(valuesWoNa))
        globalCounts.date += 1
      else if (isNumberEnum(valuesWoNa, freqsWoNa))
        globalCounts.numberEnum += 1
      else if (isNumber(valuesWoNa))
        globalCounts.freeNumber += 1
      else if (isTextEnum(valuesWoNa, freqsWoNa))
        globalCounts.textEnum += 1
      else
        globalCounts.freeText += 1

//      if (!isNullOrNA(valuesWoNa) && !isDate(valuesWoNa) && isNumber(valuesWoNa) && valuesWoNa.exists(s => s.count(_ == '.') > 1)) {
//        println(item.attributeName)
//        println(valuesWoNa.mkString(", "))
//        println
//      }
    }

    globalCounts
  }

  private def isNullOrNA(valuesWoNA : Set[String]) =
    valuesWoNA.size == 0

  private def isNumber(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => s.forall(c => c.isDigit || c == '.') && s.count(_ == '.') <= 1)

  private def isDate(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => dateFormats.exists { format =>
        try {
          new SimpleDateFormat(format).parse(s)
          true
        } catch {
          case e: ParseException => false
        }
      } || {
        try {
          val int = s.toInt
          int > 1900 && int < 2100
        } catch {
          case t: NumberFormatException => false
        }
      }
    )

  private def isBoolean(valuesWoNA : Set[String]) =
    isTextBoolean(valuesWoNA) || isNumberBoolean(valuesWoNA)

  private def isTextBoolean(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => textBooleanValues.contains(s.toLowerCase))

  private def isNumberBoolean(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => numBooleanValues.contains(s)) //    isNumber(valuesWoNA) && valuesWoNA.size <= 2

  private def isEnum(freqsWoNa : Seq[Double]) =
    freqsWoNa.size < enumValuesThreshold && (freqsWoNa.sum / freqsWoNa.size) > enumFrequencyThreshold

  private def isNumberEnum(valuesWoNA : Set[String], freqsWoNa : Seq[Double]) =
    isNumber(valuesWoNA) && isEnum(freqsWoNa)

  private def isTextEnum(valuesWoNA : Set[String], freqsWoNa : Seq[Double]) =
    isEnum(freqsWoNa) && valuesWoNA.exists(_.exists(_.isLetter))
}

object DeNoPaTypeStats extends GuiceBuilderRunnable[DeNoPaTypeStats] with App {
  override def main(args: Array[String]) = run
}