package runnables.denopa

import javax.inject.{Inject, Named}
import org.ada.server.models.Translation
import org.incal.core.runnables.FutureRunnable
import org.incal.play.GuiceRunnableApp
import org.ada.server.dataaccess.RepoTypes._
import org.ada.server.util.ManageResource.using
import play.api.Configuration

import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global

class ImportDeNoPaTranslations @Inject()(
    configuration: Configuration,
    translationRepo: TranslationRepo
  ) extends FutureRunnable {

  private val folder = configuration.getString("denopa.translation.import.folder").get

  private val filename_de = folder + "DeNoPa_translations_de"
  private val filename_en = folder + "DeNoPa_translations_en"

  private val filename_de_extra = folder + "DeNoPa_translations_de-extra"
  private val filename_en_extra = folder + "DeNoPa_translations_en-extra"

  override def runAsFuture = {
    // read all the lines
    val textsDe = getRecords(filename_de)
    val textsEn = getRecords(filename_en)

    val extraTextsDe = getRecords(filename_de_extra)
    val extraTextsEn = getRecords(filename_en_extra)

    val translations = (textsDe.zip(textsEn) ++ extraTextsDe.zip(extraTextsEn)).map{ case (de, en) => Translation(None, de, en)}.sortBy(_.original)
    println(translations.mkString("\n"))
    println("\n-------------------------------------------\n")

    for {
      // remove the old translations
      _ <- translationRepo.deleteAll

      // save the new ones
      _ <- translationRepo.save(translations)
    } yield
      ()
  }

  private def getRecords(filename : String) : Seq[String] = {
    using(Source.fromFile(filename)){
      source => {
        val lines = source.getLines
        lines.map(_.trim.replaceAll("\"\"","\"")).toSeq
      }
    }
  }
}

object ImportDeNoPaTranslations extends GuiceRunnableApp[ImportDeNoPaTranslations]