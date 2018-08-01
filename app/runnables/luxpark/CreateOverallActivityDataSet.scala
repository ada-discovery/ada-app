package runnables.luxpark

import javax.inject.Inject

import field.FieldTypeHelper
import dataaccess.Criterion.Infix
import dataaccess.RepoTypes.FieldRepo
import models.DataSetFormattersAndIds.FieldIdentity
import models._
import persistence.dataset.DataSetAccessorFactory
import play.api.Configuration
import play.api.libs.json.{JsNumber, JsObject, Json}
import reactivemongo.bson.BSONObjectID
import DataSetId.lux_park_clinical
import runnables.GuiceBuilderRunnable

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class CreateOverallActivityDataSet  @Inject()(
    configuration: Configuration,
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  private val ftf = FieldTypeHelper.fieldTypeFactory()
  private val timeout = 120000 millis

  val dataSetIdWithActivityEnums = Seq(
//    "lux_park.mpower_my_thoughts",
//    "lux_park.mpower_demographics",
//    "lux_park.mpower_enrollment_survey",
//    "lux_park.mpower_pd_enrollment_survey",
    ("lux_park.mpower_tapping_activity", 0),
    ("lux_park.mpower_tapping_activity2", 0),
    ("lux_park.mpower_walking_activity", 1),
    ("lux_park.mpower_walking_activity2", 1),
    ("lux_park.mpower_memory_activity", 2),
    ("lux_park.mpower_memory_activity2", 2),
    ("lux_park.mpower_voice_activity", 3),
    ("lux_park.mpower_voice_activity2", 3),
    ("lux_park.mpower_tremor_activity", 4),
    ("lux_park.mpower_tremor_activity2", 4)
  )

  val dataSetIdActivityEnumMap = dataSetIdWithActivityEnums.toMap

  val dataSetIds = dataSetIdWithActivityEnums.map(_._1)

  val mergedDataSetInfo = DataSetMetaInfo(
    None,
    "lux_park.mpower_overall_activity",
    "Overall Activity",
    0,
    false,
    BSONObjectID.parse("5845702f5399e2561261c662").get
  )

  val createdOnField =
    Field("createdOn", Some("Date Created"), FieldTypeId.Date)

  val externalIdField =
    Field("externalId", Some("External Id"), FieldTypeId.String)

  val dataGroupsField =
    Field("dataGroups", Some("Group"), FieldTypeId.Enum, false,
      Some(Map(
        "0" -> "control",
        "1" -> "parkinson"
      ))
    )

  val momentInDayField =
    Field("momentInDayFormatu002ejsonu002echoiceAnswers", Some("Moment In Day"), FieldTypeId.Enum, false,
      Some(Map(
        "0" -> "Another time",
        "1" -> "Immediately before taking Parkinson medication",
        "2" -> "Just after taking Parkinson medication (at your best)",
        "3" -> "No Tracked Medication"
      ))
    )

  val activityField =
    Field("activityType", Some("Activity Type"), FieldTypeId.Enum, false,
      Some(Map(
        "0" -> "tapping",
        "1" -> "walking",
        "2" -> "memory",
        "3" -> "voice",
        "4" -> "tremor"
      ))
    )

  val appVersionField =
    Field("appVersion", Some("App Version"), FieldTypeId.Enum, false,
      Some(Map(
        "0" -> "version 1.3,build 42",
        "1" -> "version 1.3,build 43",
        "2" -> "version 1.3 LUX,build 43",
        "3" -> "version 1.3 LUX,build 44",
        "4" -> "version 1.3 LUX,build 45",
        "5" -> "version 1.3 LUX,build 46"
      ))
    )

  val subjectIdField =
    Field("cdisc_dm_usubjd", Some("Clinical Subject Id"), FieldTypeId.String)

  val mergedFields = Seq(
    createdOnField,
    externalIdField,
    subjectIdField,
    dataGroupsField,
    momentInDayField,
    appVersionField,
    activityField
  )

  val mergedFieldMap = mergedFields.map( field => (field.name, field)).toMap

  val fieldNames = Seq(
    createdOnField,
    externalIdField,
    dataGroupsField,
    momentInDayField,
    appVersionField
  ).map(_.name)

  object ClinicalField extends Enumeration {
    val SubjectId = Value("cdisc_dm_usubjd")
    val MPowerId = Value("dm_mpowerid")
  }

  private val luxParkDsa = dsaf(lux_park_clinical).get

  private val appVersionFieldType = ftf(appVersionField.fieldTypeSpec)
  private val externalIdFieldType = ftf(externalIdField.fieldTypeSpec)

  override def run() = {
    val future = for {
      // register a new data set and object dsa
      newDsa <- dsaf.register(mergedDataSetInfo, None, None)

      // collect all the items from mPower activity data sets and match them with clinical subject id
      mergedJsons <- createJsons

      // first delete all items from the new data set
      _ <- newDsa.dataSetRepo.deleteAll

      // then, save the merged items to the new data set
      _ <- newDsa.dataSetRepo.save(mergedJsons)

      // delete all the fields
      _ <- newDsa.fieldRepo.deleteAll

      // save the fields
      _ <- newDsa.fieldRepo.save(mergedFields)
    } yield
      ()

    Await.result(future, timeout)
  }

  def createJsons: Future[Traversable[JsObject]] =
    for {
      // collect all the items from mPower activity data sets
      externalIdMPowerItemsItems <- Future.sequence(
        dataSetIds.map { dataSetId =>

          val dsa = dsaf(dataSetId).get
          val dataSetRepo = dsa.dataSetRepo

          for {
            // _ <- checkFields(dsa.fieldRepo, dataSetId)
            fields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))

            items <- dataSetRepo.find(projection = fieldNames)

          } yield {

            val fieldMap = fields.map( field => (field.name, field)).toMap
            items.map { item =>

              def fieldValueAsString(fieldName: String) = {
                val field = fieldMap.get(fieldName).getOrElse(
                  throw new AdaException(s"The field ${fieldName} not found in the data set $dataSetId.")
                )
                ftf(field.fieldTypeSpec).jsonToDisplayString(item \ fieldName)
              }

              val originalAppVersionString = fieldValueAsString(appVersionField.name)
              val externalId = fieldValueAsString(externalIdField.name)

              val newAppVersionJson = appVersionFieldType.displayStringToJson(originalAppVersionString)

              val json =
                item ++ Json.obj(
                  (appVersionField.name, newAppVersionJson),
                  (externalIdField.name, externalId),
                  (activityField.name, JsNumber(dataSetIdActivityEnumMap.get(dataSetId).get))
                )
              (externalId, json)
            }
          }
        }
      )

      externalIdMPowerItems = externalIdMPowerItemsItems.flatten

      // get clinical items for given mpower ids
      clinicalJsons <- luxParkDsa.dataSetRepo.find(
        criteria = Seq(ClinicalField.MPowerId.toString #-> externalIdMPowerItems.map(_._1).toSet.toSeq),
        projection = ClinicalField.values.map(_.toString)
      )
    } yield {
      import ClinicalField._

      val mPowerIdSujectIdMap: Map[String, String] =
        clinicalJsons.map { clinicalJson =>
          val mPowerId = (clinicalJson \ MPowerId.toString).get.as[String]
          val subjectId = (clinicalJson \ SubjectId.toString).get.as[String]
          (mPowerId, subjectId)
        }.toMap

      externalIdMPowerItems.map { case (mPowerId, mPowerJson) =>
        mPowerIdSujectIdMap.get(mPowerId).map( subjectId =>
          mPowerJson ++ Json.obj(SubjectId.toString -> subjectId)
        )
      }.flatten
    }

  private def checkFields(fieldRepo: FieldRepo, dataSetId: String): Future[Unit] = {
    fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames)).map { fields =>
      // validate the fields

      val nameFieldMap = fields.map(field => (field.name, field)).toMap

      fieldNames.foreach { fieldName =>
        nameFieldMap.get(fieldName) match {
          case Some(field) => {
            val mergedField = mergedFieldMap.get(fieldName).get

            if (!mergedField.name.equals(field.name))
              throw new AdaException(s"The name of the merged field '${fieldName}' differs from the one provided in the data set '${dataSetId}'.")

            if (!mergedField.label.equals(field.label))
              throw new AdaException(s"The label of the merged field '${fieldName}' differs from the one provided in the data set '${dataSetId}'.")

            if (!mergedField.numValues.equals(field.numValues))
              throw new AdaException(s"The enum values of the merged field '${fieldName}' differs from the one provided in the data set '${dataSetId}'.")
          }
          case None =>
            throw new AdaException(s"Field '${fieldName}' not found in the data set '${dataSetId}'.")
        }
      }
    }
  }
}

object CreateOverallActivityDataSet extends GuiceBuilderRunnable[CreateOverallActivityDataSet] with App { run }
