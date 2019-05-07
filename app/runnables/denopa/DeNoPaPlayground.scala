package runnables.denopa

import java.io.{File, PrintWriter}
import javax.inject.Inject

import org.ada.server.models.FieldTypeId
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.incal.core.dataaccess.Criterion.Infix
import play.api.Configuration

import scala.concurrent.Future
import scala.io.Source
import org.ada.server.dataaccess.JsonUtil
import org.incal.core.runnables.FutureRunnable
import org.incal.play.GuiceRunnableApp
import org.ada.server.dataaccess.RepoTypes.TranslationRepo

import scala.concurrent.ExecutionContext.Implicits.global

class DeNoPaPlayground @Inject() (
    configuration: Configuration,
    translationRepo: TranslationRepo,
    dsaf: DataSetAccessorFactory
  ) extends FutureRunnable {

  private val baseLineDsa = dsaf("denopa.raw_clinical_baseline").get
  private val firstVisitDsa = dsaf("denopa.raw_clinical_first_visit").get
  private val secondVisitDsa = dsaf("denopa.raw_clinical_second_visit").get

  private val folder = "/home/peter.banda/Data/DeNoPa/translations/final/"

  private val filename_de = folder + "DeNoPa_dictionary_de"
  private val filename_en = folder + "DeNoPa_dictionary_en-utf8"

  private val filename_extra_de = folder + "DeNoPa_dictionary_extra_de-utf8"
  private val filename_extra_en = folder + "DeNoPa_dictionary_extra_en-utf8"

  private val filename_second_visit_de = folder + "DeNoPa_secondvisit_dictionary_de"
  private val filename_second_visit_en = folder + "DeNoPa_secondvisit_dictionary_en"

//  val filename_de = "/Users/peter.banda/Documents/DeNoPa/translations/DeNoPa_dictionary_de"
//  val filename_en = "/Users/peter.banda/Documents/DeNoPa/translations/DeNoPa_dictionary_en2"

  private val truthValues = List("na", "ja", "nein", "falsch", "richtig", "fehlend")

  override def runAsFuture =
    for {
      baselineEnumTexts <- collectEnumTexts(baseLineDsa)
      firstVisitEnumTexts <- collectEnumTexts(firstVisitDsa)
      secondVisitEnumTexts <- collectEnumTexts(secondVisitDsa)

      baselineStringTexts <- collectStringTexts(baseLineDsa)
      firstVisitStringTexts <- collectStringTexts(firstVisitDsa)
      secondVisitStringTexts <- collectStringTexts(secondVisitDsa)

      translations <- translationRepo.find()
    } yield {
      val mergedEnumTexts = (baselineEnumTexts ++ firstVisitEnumTexts ++ secondVisitEnumTexts).toSet.toSeq.sorted
      val mergedStringTexts = (baselineStringTexts ++ firstVisitStringTexts ++ secondVisitStringTexts).toSet.toSeq.sorted

      println
      println("Baseline Enum Texts    : " + baselineEnumTexts.size)
      println("First Visit Enum Texts : " + firstVisitEnumTexts.size)
      println("Second Visit Enum Texts: " + secondVisitEnumTexts.size)
      println("-------------------------------")
      println("All Enum Texts         : " + mergedEnumTexts.size)


      println
      println("Baseline String Texts    : " + baselineStringTexts.size)
      println("First Visit String Texts : " + firstVisitStringTexts.size)
      println("Second Visit String Texts: " + secondVisitStringTexts.size)
      println("-------------------------------")
      println("All String Texts         : " + mergedStringTexts.size)
      println

      // read all the lines
      val oldTextsDe = getRecords(filename_de)
      val oldTextsEn = getRecords(filename_en)

      val newTextsDe = getRecords(filename_extra_de)
      val newTextsEn = getRecords(filename_extra_en)

      val secondVisitTextsDe = getRecords(filename_second_visit_de)
      val secondVisitTextsEn = getRecords(filename_second_visit_en)

      val mergedFileTexts = oldTextsDe.zip(oldTextsEn) ++ newTextsDe.zip(newTextsEn) ++ secondVisitTextsDe.zip(secondVisitTextsEn)
      val mergedFileTextsDe = mergedFileTexts.map(_._1)


      //
      //      val fileTextDeDuplicates = mergedFileTextsDeAll.groupBy(identity).collect { case (x, Seq(_,_,_*)) => x }
      //
      //      println("File DE Duplicates:")
      //      println(fileTextDeDuplicates.mkString("\n"))


      // calc  the diffs

      val enumDiffTexts = mergedEnumTexts.diff(mergedFileTextsDe)
      val stringDiffTexts = mergedStringTexts.diff(mergedFileTextsDe)

      val mergedEnumStringTexts = mergedEnumTexts.toSet ++ mergedStringTexts.toSet
      val extraFileTextsDe = mergedFileTextsDe.diff(mergedEnumStringTexts.toSeq).toSet


      //      val extraTranslations = translations.map(_.original).toSeq.diff(mergedEnumStringTexts.toSeq).sorted
      //
      //      println("Extra translations: " + extraTranslations.size)
      //      println("----------------------------------------------")
      //      println(extraTranslations.mkString("\n"))
      //      println

      println("The number of enums not included in text file(s)             : " + enumDiffTexts.size)
      println("The number of strings not included in text file(s)           : " + stringDiffTexts.size)
      println("The number of translations not included in enums or strings  : " + extraFileTextsDe.size)


      // removing the extra translations

      val cleanedMergedFileTexts = mergedFileTexts.filterNot(x => extraFileTextsDe.contains(x._1))

      println("The number of translations (in the files)                          : " + mergedFileTexts.size)
      println("The number of translations (in the files) after dropping extra ones: " + cleanedMergedFileTexts.size)

      println("Export cleaned merged translations")

      val pw = new PrintWriter(new File("all_translations_de"))
      pw.write(cleanedMergedFileTexts.map(_._1).mkString("\n"))
      pw.close

      val pw2 = new PrintWriter(new File("all_translations_en"))
      pw2.write(cleanedMergedFileTexts.map(_._2).mkString("\n"))
      pw2.close
    }

  private def getRecords(filename : String) : Seq[String] = {
    val lines = Source.fromFile(filename).getLines
    lines.map(_.trim).toSeq
  }

  private def collectEnumTexts(dsa: DataSetAccessor): Future[Seq[String]] =
    for {
      enumFields <- dsa.fieldRepo.find(criteria = Seq("fieldType" #== FieldTypeId.Enum))
    } yield {
      enumFields.flatMap( field =>
        field.numValues.get.flatMap { case (_, value) =>
          if (!value.toLowerCase.equals("na") && value.exists(_.isLetter) && !truthValues.contains(value.toLowerCase)) {
            Some(value)
          } else
            None
        }
      ).toSet.toSeq.sorted
    }

  private def collectStringTexts(dsa: DataSetAccessor): Future[Seq[String]] =
    for {
      stringFields <- dsa.fieldRepo.find(criteria = Seq("fieldType" #== FieldTypeId.String))

      stringFieldNames  = stringFields.map(_.name)

      jsons <- dsa.dataSetRepo.find(projection = stringFieldNames)
    } yield
      jsons.flatMap( json =>
        stringFieldNames.flatMap(fieldName =>
          JsonUtil.toString(json \ fieldName).flatMap( value =>
            if (!value.toLowerCase.equals("na") && value.exists(_.isLetter) && !truthValues.contains(value.toLowerCase))
              Some(value)
            else
              None
          ))
      ).toSet.toSeq.sorted

//    val enumFields = Await.result(statsFuture, timeout).filter{ item =>
//      val keys = item.valueRatioMap.keySet
//      (keys.size > 1 && keys.size < 25 && keys.filterNot(_.equals("NA")).exists(_.exists(_.isLetter) && !keys.forall(s => truthValues.contains(s.toLowerCase))))
//    }
//    enumFields.map { field =>
//      //      println(s"${field.attributeName}§${field.valueRatioMap.keySet.toSeq.filterNot(_.equals("NA")).sorted.mkString("§")}")
//      field.valueRatioMap.keySet.toSeq.filter(s => !s.equals("NA") && s.exists(_.isLetter)).sorted
//    }.flatten.toSet.toSeq.sorted
//  }
}

object DeNoPaPlayground extends GuiceRunnableApp[DeNoPaPlayground]