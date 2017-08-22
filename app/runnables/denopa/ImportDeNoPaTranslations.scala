package runnables.denopa

import javax.inject.{Inject, Named}

import models.Translation
import persistence.RepoTypes._
import play.api.Configuration
import runnables.{FutureRunnable, GuiceBuilderRunnable}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global

class ImportDeNoPaTranslations @Inject()(
    configuration: Configuration,
    translationRepo: TranslationRepo
  ) extends FutureRunnable {

  private val folder = configuration.getString("denopa.translation.import.folder").get

  private val filename_de = folder + "DeNoPa_dictionary_de"
  private val filename_en = folder + "DeNoPa_dictionary_en-utf8"

  private val filename_extra_de = folder + "DeNoPa_dictionary_extra_de-utf8"
  private val filename_extra_en = folder + "DeNoPa_dictionary_extra_en-utf8"

  private val truthValues = List("na", "ja", "nein", "falsch", "richtig", "fehlend")

  override def runAsFuture = {
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

    for {
      // remove all items from the collection
      _ <- translationRepo.deleteAll

      _ <- Future.sequence(
        (oldTexts ++ newTexts).sortBy(_.original).map { translation =>
          translationRepo.save(translation)
        }
      )
    } yield
      ()
  }

  private def getRecords(filename : String) : Seq[String] = {
    val lines = Source.fromFile(filename).getLines
    lines.map(_.trim.replaceAll("\"\"","\"")).toSeq
  }
}

object ImportDeNoPaTranslations extends GuiceBuilderRunnable[ImportDeNoPaTranslations] with App { run }