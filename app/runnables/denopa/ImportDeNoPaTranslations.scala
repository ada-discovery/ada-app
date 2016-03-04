package runnables.denopa

import javax.inject.{Inject, Named}

import models.Translation
import persistence.RepoTypes._
import play.api.Configuration
import runnables.GuiceBuilderRunnable

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

class ImportDeNoPaTranslations @Inject()(
    configuration: Configuration,
    translationRepo: TranslationRepo
  ) extends Runnable {

  val folder = configuration.getString("denopa.translation.import.folder").get

  val filename_de = folder + "DeNoPa_dictionary_de"
  val filename_en = folder + "DeNoPa_dictionary_en-utf8"

  val filename_extra_de = folder + "DeNoPa_dictionary_extra_de-utf8"
  val filename_extra_en = folder + "DeNoPa_dictionary_extra_en-utf8"

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

object ImportDeNoPaTranslations extends GuiceBuilderRunnable[ImportDeNoPaTranslations] with App {
  run
}