package runnables.core

import java.awt.Dimension
import java.io.{File, IOException}
import java.{util => ju}

import _root_.util.seqFutures
import dataaccess.RepoTypes.FieldRepo
import models.DataSetFormattersAndIds.FieldIdentity
import models.{Field, FieldTypeId}

import scala.concurrent.ExecutionContext.Implicits.global
import dataaccess.Criterion._

import scala.concurrent.Future

object CalcUtil {

  def numericFields(
    fieldRepo: FieldRepo)(
    featuresNum: Option[Int],
    allFeaturesExcept: Seq[String]
  ): Future[Traversable[Field]] = {
    if (featuresNum.isDefined)
      fieldRepo.find(
        Seq("fieldType" #-> Seq(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Date)),
        limit = Some(featuresNum.get)
      )
    else
      fieldRepo.find(
        Seq(
          "fieldType" #-> Seq(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Date),
          FieldIdentity.name #!-> allFeaturesExcept
        )
      )
  }

  object repeatWithTime {

    def apply[A](
      repetitions: Int)(
      f: => Future[A]
    ): Future[(A, Int)] = {
      assert(repetitions > 0, "Repetitions must be > 0.")
      val calcStart = new ju.Date
      seqFutures(1 to repetitions) { _ => f }.map { results =>
        val execTimeMs = new ju.Date().getTime - calcStart.getTime
        val execTimeSec = execTimeMs.toDouble / (1000 * repetitions)
        (results.head, execTimeSec.toInt)
      }
    }
  }

  object repeatWithTimeOptional {

    def apply[A](
      repetitions: Int)(
      f: => Future[A]
    ): Future[Option[(A, Int)]] =
      if (repetitions > 0)
        repeatWithTime(repetitions)(f).map(Some(_))
      else
        Future(None)
  }
}