package runnables.ppmi

import javax.inject.Inject

import org.incal.core.dataaccess.Criterion._
import org.incal.core.util.GroupMapList
import org.incal.core.runnables.FutureRunnable
import org.ada.server.field.{FieldType, FieldTypeHelper}
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.{Field, FieldTypeId, StorageType}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json._
import org.ada.server.services.DataSetService

import scala.concurrent.ExecutionContext.Implicits.global

class CreatePPMIUPSITData @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends FutureRunnable {

  private val logger = Logger // (this.getClass())

  private val dataSetId = "ppmi.clinical_visit"
  private val newDataSetId = "ppmi.clinical_visit_upsit"
  private val newDataSetName = "PPMI Clinical Visit UPSIT"

  private val subjectIdFieldName = "PATNO"
  private val genderFieldName = "SCREENGENDER"
  private val booleanFAMFieldNames = Seq(
    "FAMHXPDBIODADPD", "FAMHXPDBIOMOMPD", "FAMHXPDHAFSIBPD", "FAMHXPDKIDSPD"
  )
  private val integerFAMFieldNames = Seq(
    "FAMHXPDFULSIBPD", "FAMHXPDMAGPARPD", "FAMHXPDMATAUPD", "FAMHXPDPAGPARPD", "FAMHXPDPATAUPD"
  )

  private val fieldNames = Seq(
    subjectIdFieldName,
    genderFieldName,
    "RANDOMAGE",
    "UPSITUPSIT_Raw_Score"
  ) ++ booleanFAMFieldNames ++ integerFAMFieldNames

  private val newGenderField = Field(
    "GENDER_EXT", Some("Gender"), FieldTypeId.Enum, false, Map("1" -> "Male","2" -> "Female")
  )

  private val genderConversion = Map(0 -> 2, 1 -> 2, 2 -> 1)

  private val newFamilyHistoryField = Field("PD_FAMILY_HISTORY_EXT", Some("PD Family History"), FieldTypeId.Boolean)

  private val idName = JsObjectIdentity.name
  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val newGenderFieldType = ftf(newGenderField.fieldTypeSpec).asValueOf[Int]
  private val newFamilyHistoryFieldType = ftf(newFamilyHistoryField.fieldTypeSpec).asValueOf[Boolean]

  override def runAsFuture =
    for {
      // data set accessor
      dsa <- dsaf.getOrError(dataSetId)
      repo = dsa.dataSetRepo
      fieldRepo = dsa.fieldRepo

      // register a data set
      newDsa <- dataSetService.register(dsa, newDataSetId, newDataSetName, StorageType.ElasticSearch)

      // get the items
      jsons <- repo.find(projection = fieldNames ++ Seq(idName))

      // get the fields
      fields <- fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))

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
            val fieldNameValues = fieldDistinctValues.map(_._1).zip(values)

            val jsValues = fieldNameValues.map { case (fieldName, value) =>
              val fieldType = fieldTypeMap.get(fieldName).get
              val jsValue = fieldType.asValueOf[Any].valueToJson(value)
              (fieldName, jsValue)
            }

            val fieldNameValueMap = fieldNameValues.toMap

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
              jsValues ++ Seq(
                (newGenderField.name, newGenderJsValue),
                (newFamilyHistoryField.name, newFAMJsValue)
              )
            )
          }
        }

        dataSetService.saveOrUpdateRecords(newDsa.dataSetRepo, newJsons.toSeq, None, false, None, Some(100))
      }

      // save the dictionary
      _ <- {
        val newFields = fields ++ Seq(newGenderField, newFamilyHistoryField)
        dataSetService.updateFields(newDsa.fieldRepo, newFields, false, true)
      }
    } yield
      ()
}