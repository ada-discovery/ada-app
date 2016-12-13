package runnables.luxpark

import javax.inject.Inject

import dataaccess.FieldTypeHelper
import dataaccess.Criterion.Infix
import dataaccess.RepoTypes.FieldRepo
import models.DataSetFormattersAndIds.FieldIdentity
import models._
import persistence.dataset.DataSetAccessorFactory
import play.api.Configuration
import play.api.libs.json.{JsNumber, JsObject}
import reactivemongo.bson.BSONObjectID
import runnables.GuiceBuilderRunnable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class CreateOverallActivityDataSet  @Inject()(
    configuration: Configuration,
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  private val ftf = FieldTypeHelper.fieldTypeFactory
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
    BSONObjectID("5845702f5399e2561261c662")
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

  val mergedFields = Seq(
    createdOnField,
    externalIdField,
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

  override def run() = {
    val future = for {
      // register a new data set and object dsa
      newDsa <- dsaf.register(mergedDataSetInfo, None, None)

      // collect all the items from mPower activity data sets
      mergedItems <- Future.sequence(
        dataSetIds.map { dataSetId =>

          val dsa = dsaf(dataSetId).get
          val dataSetRepo = dsa.dataSetRepo

          for {
//            _ <- checkFields(dsa.fieldRepo, dataSetId)
            fields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames))

            items <- dataSetRepo.find(projection = fieldNames)

          } yield {
            val appVersionFieldType = ftf(appVersionField.fieldTypeSpec)

            val fieldMap = fields.map( field => (field.name, field)).toMap
            items.map { item =>
              val originalAppVersionField = fieldMap.get(appVersionField.name).get
              val originalAppVersionFieldType = ftf(originalAppVersionField.fieldTypeSpec)
              val originalAppVersionString = originalAppVersionFieldType.jsonToDisplayString(item \ appVersionField.name)

              val newAppVersionJson = appVersionFieldType.displayStringToJson(originalAppVersionString)

              item.++(
                JsObject(Seq(
                  (appVersionField.name, newAppVersionJson),
                  (activityField.name, JsNumber(dataSetIdActivityEnumMap.get(dataSetId).get))
                ))
              )
            }
          }
        }
      )

      // first delete all items from the new data set
      _ <- newDsa.dataSetRepo.deleteAll

      // then, save the merged items to the new data set
      _ <- newDsa.dataSetRepo.save(mergedItems.flatten)

      // delete all the fields
      _ <- newDsa.fieldRepo.deleteAll

      // save the fields
      _ <- newDsa.fieldRepo.save(mergedFields)
    } yield
      ()

    Await.result(future, timeout)
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
