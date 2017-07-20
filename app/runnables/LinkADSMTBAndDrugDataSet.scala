package runnables

import java.{util => ju}
import javax.inject.Inject

import dataaccess.Criterion.Infix
import dataaccess.{FieldTypeHelper, NotEqualsNullCriterion}
import models._
import persistence.dataset.DataSetAccessorFactory
import services.DataSetService

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class LinkADSMTBAndDrugDataSet @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends Runnable {

  private val globalDataSetId = "adsm-tb.global"
  private val globalDsa = dsaf(globalDataSetId).get
  private val globalDataSetRepo = globalDsa.dataSetRepo
  private val globalFieldRepo = globalDsa.fieldRepo

  private val drugDataSetId = "adsm-tb.drug"
  private val drugDsa = dsaf(drugDataSetId).get
  private val drugDataSetRepo = drugDsa.dataSetRepo
  private val drugFieldRepo = drugDsa.fieldRepo

  private val linkedDataSetId = "adsm-tb.global_w_drugs"
  private val linkedDataSetName = "Global with Drugs"

  private def normDataSetSetting = DataSetSetting(
    None,
    linkedDataSetId,
    "_id",
    None,
    None,
    None,
    "sampletypeid",
    None,
    None,
    false,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.ElasticSearch,
    false
  )

  private val timeout = 10 minutes

  private val drugCodeFieldName1 = "drug_coded"
  private val drugCodeFieldName2 = "id"
  private val drugFieldName2 = "name"

  private val newDrugField = Field("drug_harmonized_name", Some("Harmonized Drug Name"), FieldTypeId.Enum)

  private val ftf = FieldTypeHelper.fieldTypeFactory

  override def run = {
    val future = for {
      // get the data set meta info
      globalMetaInfo <- globalDsa.metaInfo

      // register the linked data set (if not registered already)
      mergedDsa <- dsaf.register(
        globalMetaInfo.copy(_id = None, id = linkedDataSetId, name = linkedDataSetName, timeCreated = new ju.Date()),
        Some(normDataSetSetting),
        None
      )

      // get all the global fields
      globalFields <- globalFieldRepo.find()

      // get the global drug field
      Some(globalDrugCodeField) <- globalFieldRepo.get(drugCodeFieldName1)
      globalDrugCodeFieldType = ftf(globalDrugCodeField.fieldTypeSpec)

      // get all the drug fields
      drugFields <- drugFieldRepo.find()

      // group the global items by drug code
      drugCodeGrouppedGlobalItems <- globalDataSetRepo.find().map { jsons =>
        jsons.map { json =>
          val drugCode = globalDrugCodeFieldType.jsonToDisplayString(json \ drugCodeFieldName1)
          (drugCode, json)
        }.groupBy(_._1)
      }

      refDrugCodes = drugCodeGrouppedGlobalItems.map(_._1)

      // clinical items
      drugCodeHarmonizedNameMap <- drugDataSetRepo.find(
        criteria = Seq(drugCodeFieldName2 #-> refDrugCodes.toSeq),
        projection = Seq(drugCodeFieldName2, drugFieldName2)
      ).map { jsons =>
        jsons.map { json =>
          val drugCode = (json \ drugCodeFieldName2).as[String]
          val drugName = (json \ drugFieldName2).as[String]
          (drugCode, drugName)
        }.toMap
      }

      // update the merged dictionary
      _ <- {
        val fieldNameAndTypes = (globalFields ++ Seq(newDrugField)).map( field => (field.name, field.fieldTypeSpec))
        dataSetService.updateDictionary(linkedDataSetId, fieldNameAndTypes, false, true)
      }

//      mergedJsons = {
//        drugCodeGrouppedGlobalItems.map { case (bloodKit, clinicalJson) =>
//          biosampleKitMap.get(bloodKit).map( biosampleJsons =>
//            biosampleJsons.map { case (_, biosampleJson) =>
//              clinicalJson.++(biosampleJson)
//            }
//          )
//        }.flatten.flatten
//      }

      // delete all from the old data set
      _ <- mergedDsa.dataSetRepo.deleteAll

      // process and save jsons
//      _ <- dataSetService.saveOrUpdateRecords(mergedDsa.dataSetRepo, mergedJsons.toSeq, None, false, None, Some(100))
    } yield
      ()

    Await.result(future, timeout)
  }
}

object LinkADSMTBAndDrugDataSet extends GuiceBuilderRunnable[LinkADSMTBAndDrugDataSet] with App { run }