package runnables.luxpark

import javax.inject.Inject

import org.incal.core.dataaccess.Criterion._
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import org.incal.core.runnables.FutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global

class VerifySubjectInfoForPlateSamples @Inject()(dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val logger = Logger // (this.getClass())

  private val plateSampleDataSetId = "lux_park.plate_sample_with_subject_oct_17"
  private val clinicalDataSetId = "lux_park.clinical"

  private val clinicalBloodKitField = "ibbl_kit_blood"

  private val subjectIdField = "cdisc_dm_usubjd"
  private val genderField = "cdisc_dm_sex"
  private val visitField = "redcap_event_name"
  private val clinicalFields = Seq(subjectIdField, genderField, visitField)

  private val sampleIdField = "SampleId"
  private val plateIdField = "PlateId"

  private val idName = JsObjectIdentity.name

  override def runAsFuture =
    for {
      // data set accessors
      plateSampleDsa <- dsaf.getOrError(plateSampleDataSetId)
      clinicalDsa <- dsaf.getOrError(clinicalDataSetId)

      // get all the sample items
      sampleJsons <- plateSampleDsa.dataSetRepo.find()
      bloodKitNumSampleJsonMap = sampleJsons.map { sampleJson =>
        val bloodKitNum = (sampleJson \ sampleIdField).as[String].substring(0, 15)
        (bloodKitNum, sampleJson)
      }.toMap

      // get the clinical items matching the collected blood kit num
      clinicalJsons <- clinicalDsa.dataSetRepo.find(
        criteria = Seq(clinicalBloodKitField #-> bloodKitNumSampleJsonMap.keys.toSeq),
        projection = clinicalFields ++ Seq(clinicalBloodKitField)
      )
    } yield {
      val clinicalBloodKitNumJsonMap =
        clinicalJsons.map { clinicalJson =>
          val bloodKitNum = (clinicalJson \ clinicalBloodKitField).as[String]
          (bloodKitNum, clinicalJson.-(clinicalBloodKitField))
        }.toMap

      bloodKitNumSampleJsonMap.foreach { case (bloodKitNum, sampleJson) =>
        clinicalBloodKitNumJsonMap.get(bloodKitNum).map{ clinicalJson =>
          val subjectId1 = (clinicalJson \ subjectIdField).as[String]
          val gender1 = (clinicalJson \ genderField).as[Int]
          val visit1 = (clinicalJson \ visitField).as[Int]

          val subjectId2 = (sampleJson \ subjectIdField).as[String]
          val gender2 = (sampleJson \ genderField).as[Int]
          val visit2 = (sampleJson \ visitField).as[Int]

          if (!subjectId1.equals(subjectId2) || !gender1.equals(gender2) || !visit1.equals(visit2)) {
            logger.info(s"Inconsistent blood kit num '$bloodKitNum'.")
          } else {
            logger.info(s"OK: blood kit num '$bloodKitNum': $subjectId1 = $subjectId2, $gender1 = $gender2, $visit1 = $visit2, .")
          }
        }.getOrElse {
          logger.info(s"Missing blood kit num '$bloodKitNum'.")
        }
      }
    }
}