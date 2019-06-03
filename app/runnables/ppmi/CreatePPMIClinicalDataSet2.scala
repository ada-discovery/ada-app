package runnables.ppmi

import javax.inject.Inject

import org.ada.server.field.{FieldType, FieldTypeHelper}
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.{Field, FieldTypeId, StorageType}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json.{JsObject, _}
import org.incal.core.runnables.FutureRunnable
import org.incal.core.util.GroupMapList
import org.incal.core.dataaccess.Criterion._
import org.ada.server.services.DataSetService

import scala.concurrent.ExecutionContext.Implicits.global

class CreatePPMIClinicalDataSet2 @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends FutureRunnable {

  private val logger = Logger // (this.getClass())

  private val dataSetId = "ppmi.clinical_visit"
  private val newDataSetId = "ppmi.clinical_visit_test_2"
  private val newDataSetName = "PPMI Clinical Visit Test 2"

  private val subjectIdFieldName = "PATNO"
  private val visitFieldName = "VISIT_NAME"
  private val genderFieldName = "SCREENGENDER"

  private val booleanFAMFieldNames = Seq(
    "FAMHXPDBIODADPD", "FAMHXPDBIOMOMPD", "FAMHXPDHAFSIBPD", "FAMHXPDKIDSPD"
  )
  private val integerFAMFieldNames = Seq(
    "FAMHXPDFULSIBPD", "FAMHXPDMAGPARPD", "FAMHXPDMATAUPD", "FAMHXPDPAGPARPD", "FAMHXPDPATAUPD"
  )

  private val fieldNames = Seq(
    genderFieldName,
    "RANDOMAGE",
    "SOCIOECOEDUCYRS",
    "UPSITUPSIT_Raw_Score",
    "NUPDRS3PartI_Score",
    "NUPDRS3PartII_Score",
    "NUPDRS3PartIII_Score",
    "NUPDRS3UPDRS_Total_Score",
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
    "SDMSDMTOTAL"
  ) ++ booleanFAMFieldNames ++ integerFAMFieldNames

  private val fullFieldNames = Seq(subjectIdFieldName, visitFieldName) ++ fieldNames

  object Visit extends Enumeration {
    val Baseline = Value("BL Baseline")
    val Screening = Value("SC Screening Visit")
    val Undefined = Value("")
  }

  import Visit._

  private val fieldNameVisitMap = Map(
    "SCREENGENDER" -> Undefined,
    "RANDOMAGE" -> Undefined,
    "SOCIOECOEDUCYRS"-> Undefined,
    "FAMHXPDBIODADPD" -> Undefined,
    "FAMHXPDBIOMOMPD" -> Undefined,
    "FAMHXPDFULSIBPD" -> Undefined,
    "FAMHXPDHAFSIBPD" -> Undefined,
    "FAMHXPDKIDSPD" -> Undefined,
    "FAMHXPDMAGPARPD" -> Undefined,
    "FAMHXPDMATAUPD" -> Undefined,
    "FAMHXPDPAGPARPD" -> Undefined,
    "FAMHXPDPATAUPD" -> Undefined,
    "UPSITUPSIT_Raw_Score" -> Undefined,
    "NUPDRS3PartI_Score" -> Baseline,
    "NUPDRS3PartII_Score" -> Baseline,
    "NUPDRS3PartIII_Score" -> Baseline,
    "NUPDRS3UPDRS_Total_Score" -> Baseline,
    "MOCAMCATOT" -> Screening,
    "REMSLEEPREM_Sleep_Behavior_Score" -> Baseline,
    "EPWORTHESS" -> Baseline,
    "QUIPCSQUIP_Score" -> Baseline,
    "BENTONODSummary_Score" -> Baseline,
    "MODSEADLMSEADLG" -> Screening, // SC Screening Visit (& BL Baseline)
    "GDSSHORTGDS4" -> Baseline,
    "GDSSHORTGDS10" -> Baseline,
    "GDSSHORTGDS15" -> Baseline,
    "SCOPAAUTTotal_autonomic" -> Baseline,
    "STAIS_Anxiety" -> Baseline,
    "STAIT_Anxiety" -> Baseline,
    "SFTTotal_Semantic_Fluency_Score" -> Baseline,
    "SDMSDMTOTAL" -> Baseline
  )

  private val idName = JsObjectIdentity.name
  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val newGenderField = Field("GENDER_EXT", Some("Gender"), FieldTypeId.Enum, false,
    Map("1" -> "Male","2" -> "Female")
  )
  private val genderConversion = Map(0 -> 2, 1 -> 2, 2 -> 1)

  private val newFamilyHistoryField = Field("PD_FAMILY_HISTORY_EXT", Some("PD Family History"), FieldTypeId.Boolean)

  private val newGenderFieldType = ftf(newGenderField.fieldTypeSpec).asValueOf[Int]
  private val newFamilyHistoryFieldType = ftf(newFamilyHistoryField.fieldTypeSpec).asValueOf[Boolean]

  override def runAsFuture = {
    val dsa = dsaf(dataSetId).get
    val repo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo

    val registerDataSetFuture = dataSetService.register(dsa, newDataSetId, newDataSetName, StorageType.ElasticSearch)
    val jsonsFuture = repo.find(projection = fullFieldNames ++ Seq(idName))
    val fieldsFuture  = fieldRepo.find(Seq(FieldIdentity.name #-> fullFieldNames))

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
        // records grouped by subject
        val subjectRecords = jsons.map { json =>
          val subjectId = (json \ subjectIdFieldName).as[Int]
          val visit = (json \ visitFieldName).asOpt[String].getOrElse("")
          (subjectId, (visit, json))
        }.toGroupMap

        val fieldNameTypeMap: Map[String, FieldType[_]] = fields.map { field =>
          (field.name, ftf(field.fieldTypeSpec))
        }.toMap

        val newJsons = subjectRecords.map { case (subjectId, visitRecords) =>
          val visitRecordsMap = Visit.values.map { visit =>
            val visitString = visit.toString.replace(" ", "")
            val records = visitRecords.filter(_._1.replace(" ", "").equals(visitString)).map(_._2)
            (visit, records)
          }.toMap

          val fieldJsValues = fieldNames.map { fieldName =>
            val visit = fieldNameVisitMap.get(fieldName).get
            val records = visitRecordsMap.get(visit).get

            val jsValue = records.flatMap { record =>
              (record \ fieldName).toOption
            }.headOption
            (fieldName, jsValue.getOrElse(JsNull))
          }

          val fieldNameValueMap = fieldJsValues.map { case (fieldName, jsValue) =>
            val fieldType = fieldNameTypeMap.get(fieldName).get
            (fieldName, fieldType.jsonToValue(jsValue))
          }.toMap

          // (new) gender
          val genderValue = fieldNameValueMap.get(genderFieldName).get
          val newGenderValue = genderValue.asInstanceOf[Option[Int]].map ( value => genderConversion.get(value).get )
          val newGenderJsValue = newGenderFieldType.valueToJson(newGenderValue)

          // overall boolean family history of PD
          val familyHistoryBooleanValues = booleanFAMFieldNames.flatMap { fieldName =>
            fieldNameValueMap.get(fieldName).get.asInstanceOf[Option[Boolean]]
          }
          val familyHistoryIntegerValues = integerFAMFieldNames.flatMap { fieldName =>
            fieldNameValueMap.get(fieldName).get.asInstanceOf[Option[Long]]
          }
          val newFAMValue = familyHistoryBooleanValues.exists(identity) || familyHistoryIntegerValues.exists(_ > 0)
          val newFAMJsValue = newFamilyHistoryFieldType.valueToJson(Some(newFAMValue))

          JsObject(
            fieldJsValues ++ Seq(
              (subjectIdFieldName, JsNumber(subjectId)),
              (newGenderField.name, newGenderJsValue),
              (newFamilyHistoryField.name, newFAMJsValue)
            )
          )
        }

        dataSetService.saveOrUpdateRecords(newDsa.dataSetRepo, newJsons.toSeq, None, false, None, Some(100))
      }

      // save the dictionary
      _ <- {
        val newFields = fields ++ Seq(newGenderField, newFamilyHistoryField)
        dataSetService.updateDictionaryFields(newDsa.fieldRepo, newFields, false, true)
      }
    } yield
      ()
  }
}