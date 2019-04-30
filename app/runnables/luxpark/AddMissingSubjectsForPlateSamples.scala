package runnables.luxpark

import javax.inject.Inject

import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import play.api.Logger
import org.incal.core.FutureRunnable
import org.incal.core.dataaccess.Criterion._
import org.ada.server.field.FieldTypeHelper
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import reactivemongo.play.json.BSONFormats._
import reactivemongo.bson.BSONObjectID
import scala.concurrent.ExecutionContext.Implicits.global

class AddMissingSubjectsForPlateSamples @Inject()(dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val logger = Logger // (this.getClass())

  private val plateSampleDataSetId = "lux_park.plate_sample_with_subject_oct_17"
  private val clinicalDataSetId = "lux_park.clinical"

  private val clinicalBloodKitField = "ibbl_kit_blood"
  private val clinicalFields = Seq("cdisc_dm_usubjd", "cdisc_dm_sex", "redcap_event_name")

  private val sampleIdField = "SampleId"

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val fixedBloodKitNumMap = Map(
    "10015180-ND-VAR" -> "10015180-nd-var",
    "10008282-ND-VAR" -> "10008282-ND-VAr",
    "10015196-ND-VAR" -> "10015196-nd-var",
    "10015187-ND-VAR" -> "10015187-nd-var",
    "10015555-ND-VAR" -> "10015555-ND-VAR 10015557-ND-VAR",
    "10012586-ND-VAR" -> "10012586-ND-VAR + 10012587-ND-VAR",
    "10015252-ND-VAR" -> "10015252-nd-var",
    "10011699-ND-VAR" -> "0011699-ND-VAR",
    "10012166-ND-VAR" -> "0012166-ND-VAR"
  )

  private val fixedSampleIdsBloodKitMap = fixedBloodKitNumMap.map { case (blookKit, bloodKit2) => (blookKit + "-BLD-DNA-01", bloodKit2) }

  override def runAsFuture = {
    val plateSampleDsa = dsaf(plateSampleDataSetId).get
    val clinicalDsa = dsaf(clinicalDataSetId).get

    for {
      // get the clinical items matching the given values
      clinicalJsons <- clinicalDsa.dataSetRepo.find(
        criteria = Seq(clinicalBloodKitField #-> fixedSampleIdsBloodKitMap.values.toSeq),
        projection = clinicalFields ++ Seq(clinicalBloodKitField)
      )

      // get the sample items matching the given keys
      sampleJsons <- plateSampleDsa.dataSetRepo.find(
        criteria = Seq(sampleIdField #-> fixedSampleIdsBloodKitMap.keys.toSeq)
      )

      // link the entries and update
      _ <- {
        val unfixedBloodKitNumClinicalJsonMap =
          clinicalJsons.map { clinicalJson =>
            val unfixedBloodKitNum = (clinicalJson \ clinicalBloodKitField).as[String]
            (unfixedBloodKitNum, clinicalJson.-(clinicalBloodKitField))
          }.toMap

        val newJsons = sampleJsons.map { sampleJson =>
          val sampleId = (sampleJson \ sampleIdField).as[String]
          val unfixedBloodKit = fixedSampleIdsBloodKitMap.get(sampleId).get

          val clinicalJson = unfixedBloodKitNumClinicalJsonMap.get(unfixedBloodKit).get
          sampleJson ++ clinicalJson
        }

        plateSampleDsa.dataSetRepo.update(newJsons)
      }
    } yield
      ()
  }
}