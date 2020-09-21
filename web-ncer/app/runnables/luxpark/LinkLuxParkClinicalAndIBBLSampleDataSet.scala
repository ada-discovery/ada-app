package runnables.luxpark

import javax.inject.Inject
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models._
import org.incal.core.runnables.FutureRunnable
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.ada.server.field.FieldUtil.specToField
import play.api.libs.json._
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.core.dataaccess.NotEqualsNullCriterion
import org.incal.play.GuiceRunnableApp
import reactivemongo.play.json.BSONFormats._
import org.ada.server.services.DataSetService

import scala.concurrent.ExecutionContext.Implicits.global

class LinkLuxParkClinicalAndIBBLSampleDataSet @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends FutureRunnable {

  private val clinicalDataSetId = "lux_park.clinical"
  private val biosampleDataSetId = "lux_park.ibbl_biosamples"
  private val linkedDataSetId = "lux_park.ibbl_biosamples_blood_patient"
  private val linkedDataSetName = "Patient Blood Biosample"

  private val clinicalBloodKitFieldName = "ibbl_kit_blood"
  private val clinicalFieldNames = Seq(
    "cdisc_dm_usubjd",
    "cdisc_dm_sex",
    "redcap_event_name",
    clinicalBloodKitFieldName
  )

  private val biosampleAliquotFieldName = "sampleid"

  private val saveBatchSize = 50

  override def runAsFuture =
    for {
      // clinical data set accessor
      clinicalDsa <- dsaf.getOrError(clinicalDataSetId)
      clinicalDataSetRepo = clinicalDsa.dataSetRepo
      clinicalFieldRepo = clinicalDsa.fieldRepo

      // biosample data set accessor
      biosampleDsa <- dsaf.getOrError(biosampleDataSetId)
      biosampleDataSetRepo = biosampleDsa.dataSetRepo
      biosampleFieldRepo = biosampleDsa.fieldRepo

      // register the merged data set (if not registered already)
      linkedDsa <- dataSetService.register(biosampleDsa, linkedDataSetId, linkedDataSetName, StorageType.ElasticSearch)

      // get the selected clinical fields
      clinicalFields <- clinicalFieldRepo.find(Seq(FieldIdentity.name #-> clinicalFieldNames))

      // get all the biosample fields
      biosampleFields <- biosampleFieldRepo.find()

      // update the merged dictionary
      _ <- {
        val fields = (clinicalFields ++ biosampleFields)
        dataSetService.updateFields(linkedDataSetId, fields, true, true)
      }

      // clinical items
      clinicalItems <- clinicalDataSetRepo.find(
        criteria = Seq(NotEqualsNullCriterion(clinicalBloodKitFieldName)),
        projection = clinicalFieldNames
      ).map { jsons =>
        jsons.map { json =>
          val bloodKit = (json \ clinicalBloodKitFieldName).as[String]
          (bloodKit, json)
        }
      }

      // clinical items
      biosampleKitMap <- biosampleDataSetRepo.find().map { jsons =>
        jsons.map { json =>
          val aliquotId = (json \ biosampleAliquotFieldName).as[String]
          if (aliquotId.size >= 15) {
            val kitId = aliquotId.substring(0, 15)
            Some((kitId, json))
          } else
            None
        }.flatten.groupBy(_._1)
      }

      linkedJsons = {
        clinicalItems.map { case (bloodKit, clinicalJson) =>
          biosampleKitMap.get(bloodKit).map(biosampleJsons =>
            biosampleJsons.map { case (_, biosampleJson) =>
              clinicalJson.++(biosampleJson)
            }
          )
        }.flatten.flatten
      }

      // delete all from the old data set
      _ <- linkedDsa.dataSetRepo.deleteAll

      // process and save jsons
      _ <- dataSetService.saveOrUpdateRecords(linkedDsa.dataSetRepo, linkedJsons.toSeq, None, false, None, Some(saveBatchSize))
    } yield
      ()
}

object LinkLuxParkClinicalAndIBBLSampleDataSet extends GuiceRunnableApp[LinkLuxParkClinicalAndIBBLSampleDataSet]