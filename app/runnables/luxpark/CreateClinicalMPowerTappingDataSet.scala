package runnables.luxpark

import javax.inject.Inject

import dataaccess.Criterion
import persistence.dataset.DataSetAccessorFactory
import play.api.Configuration
import runnables.DataSetId._
import runnables.GuiceBuilderRunnable
import Criterion.Infix
import models.FieldTypeId.Value

import scala.concurrent.duration._
import scala.concurrent.Await._
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class CreateClinicalMPowerTappingDataSet @Inject()(
    configuration: Configuration,
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  object MPowerField extends Enumeration {
    val ExternalId = Value("externalId")
    val CreatedOn = Value("createdOn")
    val LeftTappingSamples = Value("tapping_leftu002ejsonu002eTappingSamples")
    val RightTappingSamples = Value("tapping_rightu002ejsonu002eTappingSamples")
    val LeftTappingAcceleration = Value("accel_tapping_leftu002ejsonu002eitems")
    val RightTappingAcceleration = Value("accel_tapping_rightu002ejsonu002eitems")
    val MedsMoment = Value("momentInDayFormatu002ejsonu002echoiceAnswers")
  }

  object ClinicalField extends Enumeration {
    val MPowerId = Value("dm_mpowerid")
    val RightFingerTapping = Value("u_q3_4_1")
    val LeftFingerTapping = Value("u_q3_4_2")
    val RightToeTapping = Value("u_q3_7_1")
    val LeftToeTapping = Value("u_q3_7_2")
  }

  import MPowerField._
  import ClinicalField._

  private val timeout = 120000 millis
  private val luxParkDsa = dsaf(lux_park_clinical).get
  private val mPowerTappingDsa = dsaf("lux_park.mpower_tapping_activity").get
  private val mPowerTapping2Dsa = dsaf("lux_park.mpower_tapping_activity2").get
  private val luxParkDataRepo = luxParkDsa.dataSetRepo
  private val mPowerTappingDataRepo = mPowerTappingDsa.dataSetRepo

  override def run = {
//    val tappingsFuture = mPowerTappingDataRepo.find(projection = fields)
//
//    val scoresFuture = tappingsFuture.map{ tappings =>
//      tappings.map { tapping =>
//        val medsValue = (tapping \ MedsMoment.toString).get.as[String]
//        val leftTappingSamplesJson = (tapping \ LeftTappingSamples.toString).as[JsArray]
//        val rightTappingSamplesJson = (tapping  \ RightTappingSamples.toString).as[JsArray]
//        val leftTappingAccelerationJson = (tapping \ LeftTappingAcceleration.toString).as[JsArray]
//        val rightTappingAccelerationJson = (tapping \ RightTappingAcceleration.toString).as[JsArray]
//
//        val leftScore = leftTappingSamplesJson.value.size
//        val rightScore = rightTappingSamplesJson.value.size
//
//        (medsValue, leftScore, rightScore)
//      }
//    }
//
//    val future = scoresFuture.map { scores =>
//      scores.groupBy(_._1).map { case (meds, values) =>
//        println(meds)
//        val leftTappingScores = values.map(_._2)
//        val rightTappingScores = values.map(_._3)
//        println("Left: " + leftTappingScores.mkString(","))
//        println("Right: " + rightTappingScores.mkString(","))
//        println("Left Mean: " + (leftTappingScores.sum / leftTappingScores.size))
//        println("Right Mean: " + (rightTappingScores.sum / rightTappingScores.size))
//      }
//    }
//    result(future, timeout)
  }

  private def createJsons = {
    for {
      tappingItems <- mPowerTappingDataRepo.find(
        projection = MPowerField.values.map(_.toString)
      )
      tappingScores =
      tappingItems.map { tapping =>
        val externalId = (tapping \ ExternalId.toString).get.as[String]
        val medsValue = (tapping \ MedsMoment.toString).get.as[String]
        val leftTappingSamplesJson = (tapping \ LeftTappingSamples.toString).as[JsArray]
        val rightTappingSamplesJson = (tapping \ RightTappingSamples.toString).as[JsArray]
        val leftTappingAccelerationJson = (tapping \ LeftTappingAcceleration.toString).as[JsArray]
        val rightTappingAccelerationJson = (tapping \ RightTappingAcceleration.toString).as[JsArray]

        val leftScore = leftTappingSamplesJson.value.size
        val rightScore = rightTappingSamplesJson.value.size

        (externalId, (medsValue, leftScore, rightScore))
      }

      clinicalItems <- luxParkDataRepo.find(
        criteria = Seq(MPowerId.toString #-> tappingScores.map(_._1).toSeq),
        projection = ClinicalField.values.map(_.toString)
      )
    } yield {
      //      tappingScores.map { scores =>
      //        scores.groupBy(_._1).map { case (meds, values) =>
      //          println(meds)
      //          val leftTappingScores = values.map(_._2)
      //          val rightTappingScores = values.map(_._3)
      //          println("Left: " + leftTappingScores.mkString(","))
      //          println("Right: " + rightTappingScores.mkString(","))
      //          println("Left Mean: " + (leftTappingScores.sum / leftTappingScores.size))
      //          println("Right Mean: " + (rightTappingScores.sum / rightTappingScores.size))
      //        }
      //      }
    }
  }
}

object CreateClinicalMPowerTappingDataSet extends GuiceBuilderRunnable[CreateClinicalMPowerTappingDataSet] with App { run }

