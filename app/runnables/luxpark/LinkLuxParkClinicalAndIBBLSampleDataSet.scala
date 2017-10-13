package runnables.luxpark

import java.{util => ju}
import javax.inject.Inject

import _root_.util.seqFutures
import dataaccess.JsonUtil
import dataaccess.{AscSort, NotEqualsNullCriterion}
import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import models._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json._
import dataaccess.Criterion.Infix
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import runnables.{FutureRunnable, GuiceBuilderRunnable}
import services.DataSetService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class LinkLuxParkClinicalAndIBBLSampleDataSet @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends FutureRunnable {

  private val clinicalDataSetId = "lux_park.clinical"
  private val clinicalDsa = dsaf(clinicalDataSetId).get
  private val clinicalDataSetRepo = clinicalDsa.dataSetRepo
  private val clinicalFieldRepo = clinicalDsa.fieldRepo

  private val biosampleDataSetId = "lux_park.ibbl_biosamples"
  private val biosampleDsa = dsaf(biosampleDataSetId).get
  private val biosampleDataSetRepo = biosampleDsa.dataSetRepo
  private val biosampleFieldRepo = biosampleDsa.fieldRepo

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
      // register the merged data set (if not registered already)
      linkedDsa <- dataSetService.register(biosampleDsa, linkedDataSetId, linkedDataSetName, StorageType.ElasticSearch, "sampletypeid")

      // get the selected clinical fields
      clinicalFields <- clinicalFieldRepo.find(Seq(FieldIdentity.name #-> clinicalFieldNames))

      // get all the biosample fields
      biosampleFields <- biosampleFieldRepo.find()

      // update the merged dictionary
      _ <- {
        val fieldNameAndTypes = (clinicalFields ++ biosampleFields).map(field => (field.name, field.fieldTypeSpec))
        dataSetService.updateDictionary(linkedDataSetId, fieldNameAndTypes, true, true)
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

object LinkLuxParkClinicalAndIBBLSampleDataSet extends GuiceBuilderRunnable[LinkLuxParkClinicalAndIBBLSampleDataSet] with App { run }