package runnables

import java.{util => ju}
import javax.inject.Inject

import dataaccess.Criterion.Infix
import dataaccess.{FieldTypeHelper, NotEqualsNullCriterion}
import models._
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.{JsNull, JsNumber}
import services.DataSetService

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class LinkADSMTBAndDrugDataSet @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends FutureRunnable {

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

  private val drugCodeFieldName1 = "drug_coded"
  private val drugCodeFieldName2 = "id"
  private val drugFieldName2 = "name"

  private val harmonizedDrugField = Field("drug_harmonized_name", Some("Harmonized Drug Name"), FieldTypeId.Enum)

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture =
    for {
      // register the linked data set (if not registered already)
      linkedDsa <- dataSetService.register(globalDsa, linkedDataSetId, linkedDataSetName, StorageType.ElasticSearch)

      // get all the global fields
      globalFields <- globalFieldRepo.find()

      // get the global drug field
      Some(globalDrugCodeField) <- globalFieldRepo.get(drugCodeFieldName1)
      globalDrugCodeFieldType = ftf(globalDrugCodeField.fieldTypeSpec)

      // get all the drug fields
      drugFields <- drugFieldRepo.find()

      // get the global items with drug codes
      drugCodeGlobalItems <- globalDataSetRepo.find().map { jsons =>
        jsons.map { json =>
          val drugCode = globalDrugCodeFieldType.jsonToDisplayString(json \ drugCodeFieldName1)
          (drugCode, json)
        }
      }

      refDrugCodes = drugCodeGlobalItems.map(_._1).filter(_.nonEmpty).toSet

      // clinical items
      drugCodeHarmonizedNames <- drugDataSetRepo.find(
        criteria = Seq(drugCodeFieldName2 #-> refDrugCodes.toSeq),
        projection = Seq(drugCodeFieldName2, drugFieldName2)
      ).map { jsons =>
        jsons.map { json =>
          val drugCode = (json \ drugCodeFieldName2).as[String]
          val drugName = (json \ drugFieldName2).as[String]
          (drugCode, drugName)
        }.toSeq.sortBy(_._1).zipWithIndex.map { case ((drugCode, drugName), index) =>
          (drugCode, (drugName, index))
        }
      }

      // update the linked dictionary
      _ <- {
        val drugEnumMap = drugCodeHarmonizedNames.map { case (_, (drugName, index)) => (index.toString, drugName) }.toMap
        val fullHarmonizedDrugField = harmonizedDrugField.copy(numValues = Some(drugEnumMap))

        val fieldNameAndTypes = (globalFields ++ Seq(fullHarmonizedDrugField)).map(field => (field.name, field.fieldTypeSpec))
        dataSetService.updateDictionary(linkedDataSetId, fieldNameAndTypes, false, true)
      }

      linkedJsons = {
        val drugCodeHarmonizedNameMap = drugCodeHarmonizedNames.toMap
        drugCodeGlobalItems.map { case (drugCode, globalJson) =>

          val drugIndexJsValue =
            if (drugCode.nonEmpty) {
              drugCodeHarmonizedNameMap.get(drugCode).map { case (_, enumIndex) =>
                JsNumber(enumIndex)
              }.getOrElse(
                JsNull
              )
            } else
              JsNull

          globalJson.+(harmonizedDrugField.name -> drugIndexJsValue)
        }
      }

      // delete all from the old data set
      _ <- linkedDsa.dataSetRepo.deleteAll

      // process and save jsons
      _ <- dataSetService.saveOrUpdateRecords(linkedDsa.dataSetRepo, linkedJsons.toSeq, None, false, None, Some(10))
    } yield
      ()
}

object LinkADSMTBAndDrugDataSet extends GuiceBuilderRunnable[LinkADSMTBAndDrugDataSet] with App { run }