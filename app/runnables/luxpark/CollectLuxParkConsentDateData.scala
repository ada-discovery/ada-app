package runnables.luxpark

import java.text.SimpleDateFormat
import javax.inject.Inject

import dataaccess.{AscSort, Criterion}
import Criterion.Infix
import persistence.dataset.DataSetAccessorFactory
import play.api.Configuration
import runnables.DataSetId._
import runnables.GuiceBuilderRunnable
import scala.concurrent.duration._
import scala.concurrent.Await._
import play.api.libs.json.{JsObject, Json}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class CollectLuxParkConsentDateData @Inject()(
    configuration: Configuration,
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

    private val isControlField = "control_q1"
    private val genderField = "cdisc_dm_sex"
    private val consentDateField = "consent_q0b"

    private val fields = Seq(
      isControlField,
      genderField,
      consentDateField
    )

    private val timeout = 120000 millis
    private val dsa = dsaf(lux_park_clinical).get
    private val dataRepo = dsa.dataSetRepo

    override def run = {
//      val criteria = Seq(consentDateField #!= "", isControlField #!= "", genderField #!= "")
      val criteria = Seq(consentDateField #!= "", isControlField #!= "", genderField #== "2") // 1 is male, 2 is female
      val itemsRecords = dataRepo.find(criteria, Seq(AscSort(consentDateField)), fields)
      val consentDateRecordsFuture = itemsRecords.map{ records =>
        records.map { record =>
          val isControl = (record \ isControlField).get.as[String]
          val gender = (record \ genderField).get.as[String]
          val consentDate = (record \ consentDateField).get.as[String]

          (consentDate, isControl, gender)
        }
      }.map {
        consentRecords =>
          var controlCount = 0
          var pdCount = 0
          consentRecords.toSeq.sortBy(_._1).map { case (consentDate, isControl, gender) =>
            val date = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(consentDate)
            val dateString = new SimpleDateFormat("MM/dd/yyyy").format(date)
            if (isControl.equals("1"))
              controlCount += 1
            else
              pdCount += 1
            s"$dateString, ${pdCount + controlCount}, $pdCount, $controlCount"
          }
      }

      val output = result(consentDateRecordsFuture, timeout).mkString("\n")
      println(output)
  }
}

object CollectLuxParkConsentDateData extends GuiceBuilderRunnable[CollectLuxParkConsentDateData] with App { run }

