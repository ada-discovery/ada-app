package runnables.luxpark

import javax.inject.Inject

import persistence.dataset.DataSetAccessorFactory
import play.api.Configuration
import runnables.DataSetId._
import runnables.GuiceBuilderRunnable
import scala.concurrent.duration._
import scala.concurrent.Await._
import play.api.libs.json.{JsValue, JsArray, JsObject, Json}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.io.Source

class AnalyzeLuxParkMPowerTappingData @Inject()(
    configuration: Configuration,
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  private val subjectMPowerId = "01368F95"
  private val leftTappingSamplesField = "tapping_leftu002ejsonu002eTappingSamples"
  private val rightTappingSamplesField = "tapping_rightu002ejsonu002eTappingSamples"
  private val leftTappingAccelerationField = "accel_tapping_leftu002ejsonu002eitems"
  private val rightTappingAccelerationField = "accel_tapping_rightu002ejsonu002eitems"
  private val medsFields = "momentInDayFormatu002ejsonu002echoiceAnswers"

  private val synapseDataFolder = "/home/peter/Downloads/mPowerLux/syn6130513/subData/"

  private val fields = Seq(
    leftTappingSamplesField,
    rightTappingSamplesField,
    leftTappingAccelerationField,
    rightTappingAccelerationField,
    medsFields
  )

  private val timeout = 120000 millis
  private val luxParkDsa = dsaf(luxpark).get
  private val mPowerTappingDsa = dsaf("lux_park.mpower_tapping_activity").get
  private val luxParkDataRepo = luxParkDsa.dataSetRepo
  private val mPowerTappingDataRepo = mPowerTappingDsa.dataSetRepo

  override def run = {
    val filter = Some(Json.obj("externalId" -> subjectMPowerId))
    val tappingsFuture = mPowerTappingDataRepo.find(filter, None, Some(JsObject(fields.map( field => (field, Json.toJson(1))))))
    val scoresFuture = tappingsFuture.map{ tappings =>
      tappings.map { tapping =>
        val leftTappingSamplesFileId = (tapping \ leftTappingSamplesField).get.as[String]
        val rightTappingSamplesFileId = (tapping \ rightTappingSamplesField).get.as[String]
        val leftTappingAccelerationFileId = (tapping \ leftTappingAccelerationField).get.as[String]
        val rightTappingAccelerationFileId = (tapping \ rightTappingAccelerationField).get.as[String]
        val medsValue = (tapping \ medsFields).get.as[String]

        val leftTappingSamplesJson = fileToJson(leftTappingSamplesFileId)
        val rightTappingSamplesJson = fileToJson(rightTappingSamplesFileId)
        val leftTappingAccelerationJson = fileToJson(leftTappingAccelerationFileId)
        val rightTappingAccelerationJson = fileToJson(rightTappingAccelerationFileId)

        val leftScore = leftTappingSamplesJson.value.asInstanceOf[Seq[JsValue]].map(value => value.toString()).size
        val rightScore = rightTappingSamplesJson.value.asInstanceOf[Seq[JsValue]].map(value => value.toString()).size
//        println(leftTappingAccelerationJson.value.asInstanceOf[Seq[JsValue]].map(value => value.toString()).size)
//        println(rightTappingAccelerationJson.value.asInstanceOf[Seq[JsValue]].map(value => value.toString()).size)
        (medsValue, leftScore, rightScore)
      }
    }

    val future = scoresFuture.map { scores =>
      scores.groupBy(_._1).map { case (meds, values) =>
        println(meds)
        val leftTappingScores = values.map(_._2)
        val rightTappingScores = values.map(_._3)
        println("Left: " + leftTappingScores.mkString(","))
        println("Right: " + rightTappingScores.mkString(","))
        println("Left Mean: " + (leftTappingScores.sum / leftTappingScores.size))
        println("Right Mean: " + (rightTappingScores.sum / rightTappingScores.size))
      }
    }
    result(future, timeout)
  }

  private def fileToJson(fileName: String): JsArray = {
    val string = Source.fromFile(synapseDataFolder + fileName).mkString
    Json.parse(string).asInstanceOf[JsArray]
  }
}

object AnalyzeLuxParkMPowerTappingData extends GuiceBuilderRunnable[AnalyzeLuxParkMPowerTappingData] with App { run }

