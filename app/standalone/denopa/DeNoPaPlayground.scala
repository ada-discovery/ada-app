package standalone.denopa

import javax.inject.{Inject, Named}

import models.MetaTypeStats
import persistence.RepoTypeRegistry._
import persistence._
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import standalone.GuiceBuilderRunnable

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

class DeNoPaPlayground @Inject() (
    @Named("DeNoPaBaselineMetaTypeStatsRepo") baselineStatsRepo : MetaTypeStatsRepo,
    @Named("DeNoPaFirstVisitMetaTypeStatsRepo") firstVisitStatsRepo : MetaTypeStatsRepo
  ) extends Runnable {

  val filename_de = "/Users/peter.banda/Documents/DeNoPa/translations/DeNoPa_dictionary_de"
  val filename_en = "/Users/peter.banda/Documents/DeNoPa/translations/DeNoPa_dictionary_en2"
  val timeout = 120000 millis

  val truthValues = List("na", "ja", "nein", "falsch", "richtig", "fehlend")

  override def run = {
    val baselineTexts = collectTexts(baselineStatsRepo)
    val firstVisitTexts = collectTexts(firstVisitStatsRepo)
    val mergedTexts = (baselineTexts ++ firstVisitTexts).toSet.toSeq.sorted

    // read all the lines
    val oldTextsDe = getRecords(filename_de)
    val oldTextsEn = getRecords(filename_en)

//    val oldTexts = (oldTextsDe, oldTextsEn).zipped.map{ case (de, en) => de + "," + en}
//    println(oldTexts.mkString("\n"))

    val newDiffTexts = mergedTexts.diff(oldTextsDe)
    val oldDiffTexts = oldTextsDe.diff(mergedTexts)

    println(mergedTexts.size)
    println(oldTextsDe.size)
    println(newDiffTexts.size)
    println(oldDiffTexts.size)

//    println("NEW")
//    println(mergedTexts.mkString("\n"))
//    println
//    println
//    println("OLD")
//    println(oldTextsDe.mkString("\n"))
//    println

    println
    println("NEW DIFF")
    println
    println(newDiffTexts.mkString("\n"))

//    println
//    println
//    println("OLD DIFF")
//    println(oldDiffTexts.mkString("\n"))
  }

  private def getRecords(filename : String) : Seq[String] = {
    val lines = Source.fromFile(filename).getLines
    lines.map(_.trim).toSeq
  }

  private def collectTexts(repo : AsyncReadonlyRepo[MetaTypeStats, BSONObjectID]) = {
    val statsFuture = repo.find(None, Some(Json.obj("attributeName" -> 1)))
    val enumFields = Await.result(statsFuture, timeout).filter{ item =>
      val keys = item.valueRatioMap.keySet
      (keys.size > 1 && keys.size < 25 && keys.filterNot(_.equals("NA")).exists(_.exists(_.isLetter) && !keys.forall(s => truthValues.contains(s.toLowerCase))))
    }
    enumFields.map { field =>
      //      println(s"${field.attributeName}§${field.valueRatioMap.keySet.toSeq.filterNot(_.equals("NA")).sorted.mkString("§")}")
      field.valueRatioMap.keySet.toSeq.filter(s => !s.equals("NA") && s.exists(_.isLetter)).sorted
    }.flatten.toSet.toSeq.sorted
  }
}

object DeNoPaPlayground extends GuiceBuilderRunnable[DeNoPaPlayground] with App {
  run
}