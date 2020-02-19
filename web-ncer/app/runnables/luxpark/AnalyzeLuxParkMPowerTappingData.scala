package runnables.luxpark

import javax.inject.Inject

import org.incal.core.dataaccess.Criterion
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Configuration
import org.incal.play.GuiceRunnableApp
import Criterion.Infix
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
  private val luxParkDataSetId = "lux_park.clinical"

  private val luxParkDsa = dsaf(luxParkDataSetId).get
  private val mPowerTappingDsa = dsaf("lux_park.mpower_tapping_activity").get
  private val luxParkDataRepo = luxParkDsa.dataSetRepo
  private val mPowerTappingDataRepo = mPowerTappingDsa.dataSetRepo

  override def run = {
    val tappingsFuture = mPowerTappingDataRepo.find(
      criteria = Seq("externalId" #== subjectMPowerId),
      projection = fields
    )

    val scoresFuture = tappingsFuture.map{ tappings =>
      tappings.map { tapping =>
        val medsValue = (tapping \ medsFields).get.as[String]
        val leftTappingSamplesJson = readSubFieldJsonArray(tapping, leftTappingSamplesField)
        val rightTappingSamplesJson = readSubFieldJsonArray(tapping, rightTappingSamplesField)
        val leftTappingAccelerationJson = readSubFieldJsonArray(tapping, leftTappingAccelerationField)
        val rightTappingAccelerationJson = readSubFieldJsonArray(tapping, rightTappingAccelerationField)

        val leftScore = leftTappingSamplesJson.map(value => value.toString()).size
        val rightScore = rightTappingSamplesJson.map(value => value.toString()).size

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

  private def readSubFieldJsonArray(tapping: JsObject, fieldName: String): Seq[JsValue] = {
    val fileName = (tapping \ fieldName).get.as[String]
    val string = Source.fromFile(synapseDataFolder + fileName).mkString
    Json.parse(string).asInstanceOf[JsArray].value
  }
}

object AnalyzeLuxParkMPowerTappingData extends GuiceRunnableApp[AnalyzeLuxParkMPowerTappingData]
