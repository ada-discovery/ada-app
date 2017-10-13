package runnables.denopa

import javax.inject.Inject

import models.FieldTypeId
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import dataaccess.Criterion.Infix
import play.api.Configuration

import scala.concurrent.Future
import scala.io.Source
import runnables.{FutureRunnable, GuiceBuilderRunnable}
import dataaccess.JsonUtil
import persistence.RepoTypes.TranslationRepo

import scala.concurrent.ExecutionContext.Implicits.global

class DeNoPaPlayground @Inject() (
    configuration: Configuration,
    translationRepo: TranslationRepo,
    dsaf: DataSetAccessorFactory
  ) extends FutureRunnable {

  private val baseLineDsa = dsaf("denopa.raw_clinical_baseline").get
  private val firstVisitDsa = dsaf("denopa.raw_clinical_first_visit").get
  private val secondVisitDsa = dsaf("denopa.raw_clinical_second_visit").get

  private val folder = configuration.getString("denopa.translation.import.folder").get

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

      val mergedFileTextsDe = (oldTextsDe ++ newTextsDe ++ secondVisitTextsDe).toSet.toSeq.sorted
      val mergedFileTextsEn = (oldTextsEn ++ newTextsEn ++ secondVisitTextsEn).toSet.toSeq.sorted

      // calc  the diffs

      val enumDiffTexts = mergedEnumTexts.diff(mergedFileTextsDe)
      val stringDiffTexts = mergedStringTexts.diff(mergedFileTextsDe)

      val mergedEnumStringTexts = mergedEnumTexts.toSet ++ mergedStringTexts.toSet
      val fileDiffTexts = mergedFileTextsDe.diff(mergedEnumStringTexts.toSeq)

      val extraTranslations = translations.map(_.original).toSeq.diff(mergedEnumStringTexts.toSeq).sorted

      println("Extra translations: " + extraTranslations.size)
      println("----------------------------------------------")
      println(extraTranslations.mkString("\n"))
      println

      println("The number of enums not included in text file(s)             : " + enumDiffTexts.size)
      println("The number of strings not included in text file(s)           : " + stringDiffTexts.size)
      println("The number of translations not included in enums or strings  : " + fileDiffTexts.size)

      println
      println("Translations to drop:")
      println("---------------------")
      fileDiffTexts.foreach(println)
      println

      //      println(mergedEnumTexts.size)
//      println(oldTextsDe.size)
//      println(newDiffTexts.size)
//      println(oldDiffTexts.size)

      //    println("NEW")
      //    println(mergedTexts.mkString("\n"))
      //    println
      //    println
      //    println("OLD")
      //    println(oldTextsDe.mkString("\n"))
      //    println

//      println
//      println("NEW DIFF")
//      println
//      println(newDiffTexts.mkString("\n"))

      //    println
      //    println
      //    println("OLD DIFF")
      //    println(oldDiffTexts.mkString("\n"))
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

object DeNoPaPlayground extends GuiceBuilderRunnable[DeNoPaPlayground] with App { run }