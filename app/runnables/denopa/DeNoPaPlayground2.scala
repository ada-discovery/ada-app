package runnables.denopa

import javax.inject.Inject

import org.ada.server.models.FieldTypeId
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.incal.core.dataaccess.Criterion.Infix
import play.api.Configuration

import scala.concurrent.Future
import scala.io.Source
import org.ada.server.dataaccess.JsonUtil
import org.incal.core.FutureRunnable
import org.incal.play.GuiceRunnableApp
import org.ada.server.dataaccess.RepoTypes.TranslationRepo

import scala.concurrent.ExecutionContext.Implicits.global

class DeNoPaPlayground2 @Inject() (
    configuration: Configuration,
    translationRepo: TranslationRepo,
    dsaf: DataSetAccessorFactory
  ) extends FutureRunnable {

  private val baseLineDsa = dsaf("denopa.raw_clinical_baseline").get
  private val firstVisitDsa = dsaf("denopa.raw_clinical_first_visit").get
  private val secondVisitDsa = dsaf("denopa.raw_clinical_second_visit").get

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

      // calc  the diffs

      val translationsDe = translations.map(_.original).toSeq
      val enumDiffTexts = mergedEnumTexts.diff(translationsDe)
      val stringDiffTexts = mergedStringTexts.diff(translationsDe)

      val mergedEnumStringTexts = mergedEnumTexts.toSet ++ mergedStringTexts.toSet
      val extraTranslations = translationsDe.diff(mergedEnumStringTexts.toSeq).toSet

      println("The number of enums not included in translations             : " + enumDiffTexts.size)
      println(">>")
      enumDiffTexts.foreach(println)
      println
      println("The number of strings not included in translations           : " + stringDiffTexts.size)
      println("The number of translations not included in enums or strings  : " + extraTranslations.size)


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
          if (!value.toLowerCase.equals("na") && value.exists(_.isLetter)) {
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
            if (!value.toLowerCase.equals("na") && value.exists(_.isLetter))
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

object DeNoPaPlayground2 extends GuiceRunnableApp[DeNoPaPlayground2]