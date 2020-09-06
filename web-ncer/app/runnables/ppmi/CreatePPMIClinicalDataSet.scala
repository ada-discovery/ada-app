package runnables.ppmi

import javax.inject.Inject

import org.incal.core.dataaccess.Criterion._
import org.incal.core.runnables.FutureRunnable
import org.incal.core.util.GroupMapList
import org.ada.server.field.{FieldType, FieldTypeHelper}
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.StorageType
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json._
import org.ada.server.services.DataSetService

import scala.concurrent.ExecutionContext.Implicits.global

class CreatePPMIClinicalDataSet @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends FutureRunnable {

  private val logger = Logger // (this.getClass())

  private val dataSetId = "ppmi.clinical_visit"
  private val newDataSetId = "ppmi.clinical_visit_test"
  private val newDataSetName = "PPMI Clinical Visit Test"

  private val subjectIdFieldName = "PATNO"
  private val fieldNames = Seq(
    subjectIdFieldName,
    "SCREENGENDER",
    "RANDOMAGE",
//    "VISIT_NAME",
    "NUPDRS3PartI_Score",
    "NUPDRS3PartII_Score",
    "NUPDRS3PartIII_Score",
    "NUPDRS3UPDRS_Total_Score",
    "UPSITUPSIT_Raw_Score",
    "MOCAMCATOT",
    "REMSLEEPREM_Sleep_Behavior_Score",
    "EPWORTHESS",
    "QUIPCSQUIP_Score",
    "BENTONODSummary_Score",
    "MODSEADLMSEADLG",
    "GDSSHORTGDS4",
    "GDSSHORTGDS10",
    "GDSSHORTGDS15",
    "SCOPAAUTTotal_autonomic",
    "STAIS_Anxiety",
    "STAIT_Anxiety",
    "SFTTotal_Semantic_Fluency_Score",
    "SDMSDMTOTAL",
    "FAMHXPDBIODADPD",
    "FAMHXPDBIOMOMPD",
    "FAMHXPDFULSIBPD",
    "FAMHXPDHAFSIBPD",
    "FAMHXPDKIDSPD",
    "FAMHXPDMAGPARPD",
    "FAMHXPDMATAUPD",
    "FAMHXPDPAGPARPD",
    "FAMHXPDPATAUPD"
  )

  private val idName = JsObjectIdentity.name
  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture = {
    val dsa = dsaf.applySync(dataSetId).get
    val repo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo

    val registerDataSetFuture = dataSetService.register(dsa, newDataSetId, newDataSetName, StorageType.ElasticSearch)
    val jsonsFuture = repo.find(projection = fieldNames ++ Seq(idName))
    val fieldsFuture  = fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))

    for {
      // register a data set
      newDsa <- registerDataSetFuture

      // get the items
      jsons <- jsonsFuture

      // get the fields
      fields <- fieldsFuture

      // delete all the records from the new data set
      _ <- newDsa.dataSetRepo.deleteAll

      // store values
      _ <- {
        val subjectRecords = jsons.map { json =>
          val subjectId = (json \ subjectIdFieldName).as[Int]
          (subjectId, json)
        }.toGroupMap

        val fieldTypeMap: Map[String, FieldType[_]] = fields.map(field => (field.name, ftf(field.fieldTypeSpec))).toMap

        val newJsons = subjectRecords.flatMap { case (_, records) =>
          val fieldDistinctValues = fieldNames.map { fieldName =>
            val fieldType = fieldTypeMap.get(fieldName).get
            val values = records.flatMap { record =>
              fieldType.jsonToValue(record \ fieldName)
            }
            val distinctValues = values.groupBy(identity).map(_._1)
            (fieldName, distinctValues)
          }

          val newValues = records.map { record =>
            fieldDistinctValues.map { case (fieldName, distinctValues) =>
              val fieldType = fieldTypeMap.get(fieldName).get

              if (distinctValues.size == 1) {
                Some(distinctValues.head)
              } else {
                fieldType.jsonToValue(record \ fieldName)
              }
            }
          }

          val uniqueValues = newValues.groupBy(identity).map(_._1)

          uniqueValues.map { values =>
            val jsValues = fieldDistinctValues.zip(values).map { case ((fieldName, _), value) =>
              val fieldType = fieldTypeMap.get(fieldName).get
              val jsValue = fieldType.asValueOf[Any].valueToJson(value)
              (fieldName, jsValue)
            }
            JsObject(jsValues)
          }
        }

        dataSetService.saveOrUpdateRecords(newDsa.dataSetRepo, newJsons.toSeq, None, false, None, Some(100))
      }

      // save the dictionary
      _ <- dataSetService.updateFields(newDsa.fieldRepo, fields, false, true)
    } yield
      ()
  }
}
