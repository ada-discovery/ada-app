package standalone

import javax.inject.{Named, Inject}

import models.{MetaTypeStats, Translation}
import persistence.RepoTypeRegistry._
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration._
import persistence._
import scala.concurrent.Await
import scala.io.Source

class DeNoPaTranslations @Inject() (
    translationRepo: TranslationRepo,
    @Named("DeNoPaBaselineMetaTypeStatsRepo") baselineStatsRepo : MetaTypeStatsRepo,
    @Named("DeNoPaFirstVisitMetaTypeStatsRepo") firstVisitStatsRepo : MetaTypeStatsRepo
  ) extends Runnable {

//  val foldername = "/Users/peter.banda/Projects/ncer-pd/project/translations/"
//  val foldername = "/home/peter.banda/DeNoPa/translations/"
  val foldername = "/home/peter/Projects/ncer-pd/project/translations/"

  val filename_de = foldername + "DeNoPa_dictionary_de"
  val filename_en = foldername + "DeNoPa_dictionary_en-utf8"

  val filename_extra_de = foldername + "DeNoPa_dictionary_extra_de-utf8"
  val filename_extra_en = foldername + "DeNoPa_dictionary_extra_en-utf8"

  val timeout = 120000 millis

  val truthValues = List("na", "ja", "nein", "falsch", "richtig", "fehlend")

  override def run = {
    // remove all items from the collection
    Await.result(translationRepo.deleteAll, timeout)

    // read all the lines
    val oldTextsDe = getRecords(filename_de)
    val oldTextsEn = getRecords(filename_en)
    val newTextsDe = getRecords(filename_extra_de)
    val newTextsEn = getRecords(filename_extra_en)

    val oldTexts = (oldTextsDe, oldTextsEn).zipped.map{ case (de, en) => Translation(None, de, en)}
    println(oldTexts.mkString("\n"))

    println("\n-------------------------------------------\n")

    val newTexts = (newTextsDe, newTextsEn).zipped.map{ case (de, en) => Translation(None, de, en)}
    println(newTexts.mkString("\n"))

    println(oldTextsDe.size)
    println(oldTextsEn.size)

    println(newTextsDe.size)
    println(newTextsEn.size)

    (oldTexts ++ newTexts).sortBy(_.original).foreach{ translation =>
      Await.result(translationRepo.save(translation), timeout)
    }
  }

  private def getRecords(filename : String) : Seq[String] = {
    val lines = Source.fromFile(filename).getLines
    lines.map(_.trim.replaceAll("\"\"","\"")).toSeq
  }
}

object DeNoPaTranslations extends GuiceBuilderRunnable[DeNoPaTranslations] with App {
  override def main(args: Array[String]) = run
}