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
import runnables.GuiceBuilderRunnable
import services.DataSetService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class LinkLuxParkClinicalAndIBBLSampleDataSet @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends Runnable {

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

  private def linkedDataSetSetting = new DataSetSetting(linkedDataSetId, StorageType.ElasticSearch, "sampletypeid")

  private val timeout = 10 minutes

  private val clinicalBloodKitFieldName = "ibbl_kit_blood"
  private val clinicalFieldNames = Seq(
    "cdisc_dm_usubjd",
    "redcap_event_name",
    clinicalBloodKitFieldName
  )

  private val biosampleAliquotFieldName = "sampleid"

  override def run = {
    val future = for {
      // get the data set meta info
      biosampleMetaInfo <- biosampleDsa.metaInfo

      // register the merged data set (if not registered already)
      linkedDsa <- dsaf.register(
        biosampleMetaInfo.copy(_id = None, id = linkedDataSetId, name = linkedDataSetName, timeCreated = new ju.Date()),
        Some(linkedDataSetSetting),
        None
      )

      // get the selected clinical fields
      clinicalFields <- clinicalFieldRepo.find(Seq(FieldIdentity.name #-> clinicalFieldNames))

      // get all the biosample fields
      biosampleFields <- biosampleFieldRepo.find()

      // update the merged dictionary
      _ <- {
        val fieldNameAndTypes = (clinicalFields ++ biosampleFields).map( field => (field.name, field.fieldTypeSpec))
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
          biosampleKitMap.get(bloodKit).map( biosampleJsons =>
            biosampleJsons.map { case (_, biosampleJson) =>
              clinicalJson.++(biosampleJson)
            }
          )
        }.flatten.flatten
      }

      // delete all from the old data set
      _ <- linkedDsa.dataSetRepo.deleteAll

      // process and save jsons
      _ <- dataSetService.saveOrUpdateRecords(linkedDsa.dataSetRepo, linkedJsons.toSeq, None, false, None, Some(100))
    } yield
      ()

    Await.result(future, timeout)
  }
}

object LinkLuxParkClinicalAndIBBLSampleDataSet extends GuiceBuilderRunnable[LinkLuxParkClinicalAndIBBLSampleDataSet] with App { run }