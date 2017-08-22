package runnables.denopa

import javax.inject.{Inject, Named}

import models.FieldTypeId
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import dataaccess.Criterion.Infix
import play.api.Configuration

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.Source
import runnables.{FutureRunnable, GuiceBuilderRunnable}
import util.JsonUtil

import scala.concurrent.ExecutionContext.Implicits.global

class DeNoPaPlayground @Inject() (
    configuration: Configuration,
    dsaf: DataSetAccessorFactory
  ) extends FutureRunnable {

  private val baseLineDsa = dsaf("denopa.raw_clinical_baseline").get
  private val firstVisitDsa = dsaf("denopa.raw_clinical_first_visit").get

  private val folder = configuration.getString("denopa.translation.import.folder").get

  private val filename_de = folder + "DeNoPa_dictionary_de"
  private val filename_en = folder + "DeNoPa_dictionary_en-utf8"

//  val filename_de = "/Users/peter.banda/Documents/DeNoPa/translations/DeNoPa_dictionary_de"
//  val filename_en = "/Users/peter.banda/Documents/DeNoPa/translations/DeNoPa_dictionary_en2"

  private val truthValues = List("na", "ja", "nein", "falsch", "richtig", "fehlend")

  override def runAsFuture =
    for {
      baselineEnumTexts <- collectEnumTexts(baseLineDsa)
      firstVisitEnumTexts <- collectEnumTexts(firstVisitDsa)

      baselineStringTexts <- collectStringTexts(baseLineDsa)
      firstVisitStringTexts <- collectStringTexts(firstVisitDsa)
    } yield {
      val mergedEnumTexts = (baselineEnumTexts ++ firstVisitEnumTexts).toSet.toSeq.sorted
      val mergedStringTexts = (baselineStringTexts ++ firstVisitStringTexts).toSet.toSeq.sorted

      println
      println("Baseline Enum Texts   : " + baselineEnumTexts.size)
      println("First Visit Enum Texts: " + firstVisitEnumTexts.size)
      println("-------------------------------")
      println("All Enum Texts        : " + mergedEnumTexts.size)

      println
      println("Baseline String Texts   : " + baselineStringTexts.size)
      println("First Visit String Texts: " + firstVisitStringTexts.size)
      println("-------------------------------")
      println("All String Texts        : " + mergedStringTexts.size)
      println

      // read all the lines
      val oldTextsDe = getRecords(filename_de)
      val oldTextsEn = getRecords(filename_en)

      //    val oldTexts = (oldTextsDe, oldTextsEn).zipped.map{ case (de, en) => de + "," + en}
      //    println(oldTexts.mkString("\n"))

      val newDiffTexts = mergedEnumTexts.diff(oldTextsDe)
      val oldDiffTexts = oldTextsDe.diff(mergedEnumTexts)

      println(mergedEnumTexts.size)
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

  private def collectEnumTexts(dsa: DataSetAccessor): Future[Traversable[String]] =
    for {
      enumFields <- dsa.fieldRepo.find(criteria = Seq("fieldType" #== FieldTypeId.Enum))
    } yield {
      enumFields.flatMap( field =>
        field.numValues.get.flatMap { case (_, value) =>
          if (!value.toLowerCase.equals("na") && value.exists(_.isLetter) && !truthValues.contains(value.toLowerCase))
            Some(value)
          else
            None
        }
      ).toSet.toSeq.sorted
    }

  private def collectStringTexts(dsa: DataSetAccessor): Future[Traversable[String]] =
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