package runnables.luxpark

import javax.inject.Inject
import java.{util => ju}

import dataaccess.{Criterion, FieldTypeHelper}
import persistence.dataset.DataSetAccessorFactory
import play.api.Configuration
import runnables.DataSetId._
import runnables.GuiceBuilderRunnable
import Criterion.Infix
import dataaccess.RepoTypes.{FieldRepo, JsonCrudRepo}
import models._
import models.FieldTypeId.Value

import scala.concurrent.duration._
import scala.concurrent.Await._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

class CreateClinicalMPowerTappingDataSet @Inject()(
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  object MPowerTappingField extends Enumeration {
    val ExternalId = Value("externalId")
    val CreatedOn = Value("createdOn")
    val MedsMoment = Value("momentInDayFormatu002ejsonu002echoiceAnswers")
    val LeftTappingSamples = Value("tapping_leftu002ejsonu002eTappingSamples")
    val RightTappingSamples = Value("tapping_rightu002ejsonu002eTappingSamples")
//    val LeftTappingAcceleration = Value("accel_tapping_leftu002ejsonu002eitems")
//    val RightTappingAcceleration = Value("accel_tapping_rightu002ejsonu002eitems")
  }

  object NewMPowerTappingField extends Enumeration {
    val CreatedOn = Value("mpower_tapping_createdOn")
    val MedsMoment = Value("mpower_tapping_meds_momentInDay")
    val LeftTappingCount = Value("mpower_tapping_left_count")
    val RightTappingCount = Value("mpower_tapping_right_count")
    val LeftTappingScore = Value("mpower_tapping_left_score")
    val RightTappingScore = Value("mpower_tapping_right_score")
  }

  object ClinicalField extends Enumeration {
    val SubjectId = Value("cdisc_dm_usubjd")
    val MPowerId = Value("dm_mpowerid")
    val Visit = Value("redcap_event_name")
    val Sex = Value("cdisc_dm_sex")
    val Age = Value("sv_age")
    val Group = Value("control_q1")
    val RightFingerTapping = Value("u_q3_4_1")
    val LeftFingerTapping = Value("u_q3_4_2")
    val RightToeTapping = Value("u_q3_7_1")
    val LeftToeTapping = Value("u_q3_7_2")
    val BasicAssessmentStartDate = Value("sv_basic_date")
    val UPSRSScorePart1 = Value("u_part1_score")
    val UPSRSScorePart2 = Value("u_part2_score")
    val UPSRSScorePart3 = Value("u_part3_score")
    val UPSRSScorePart4 = Value("u_part4_score")
  }

  case class MPowerTappingData(
    externalId: String,
    createdOn: Long,
    medsMoment: Int,
    leftTappingCount: Int,
    rightTappingCount: Int,
    leftTappingScore: Int,
    rightTappingScore: Int
  )

  implicit val mPowerTappingDataFormat = Json.format[MPowerTappingData]

  private val timeout = 120000 millis
  private val luxParkDsa = dsaf(lux_park_clinical).get
  private val mPowerTappingDsa = dsaf("lux_park.mpower_tapping_activity").get
  private val mPowerTapping2Dsa = dsaf("lux_park.mpower_tapping_activity2").get

  private val mergedDsa =
    result(
      dsaf.register(
        DataSetMetaInfo(
          None,
          "lux_park.clinical_and_mpower_tapping_activity",
          "Tapping Activity & Clinical",
          0,
          false,
          BSONObjectID.parse("5845702f5399e2561261c662").get
        ),
        Some(DataSetSetting(
          None,
          "lux_park.clinical_and_mpower_tapping_activity",
          ClinicalField.SubjectId.toString,
          None,
          Some(NewMPowerTappingField.LeftTappingScore.toString),
          Some(NewMPowerTappingField.RightTappingScore.toString),
          NewMPowerTappingField.LeftTappingScore.toString,
          None,
          None,
          false,
          None,
          Map(),
        StorageType.ElasticSearch,
          false
        )),
        None
      ),
      2 minutes
    )

  private val luxParkDataRepo = luxParkDsa.dataSetRepo
  private val mergedDataRepo = mergedDsa.dataSetRepo

  private val luxParkFieldRepo = luxParkDsa.fieldRepo
  private val mergedFieldRepo = mergedDsa.fieldRepo

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def run = {
    val future = for {
      jsons <- createJsons
      fields <- createFields
      _ <- mergedDataRepo.deleteAll
      _ <- mergedDataRepo.save(jsons)
      _ <- mergedFieldRepo.deleteAll
      _ <- mergedFieldRepo.save(fields)
    } yield
      ()

    result(future, 2 minutes)
  }

  private def countLeftRightAlternations(jsArray: JsArray): Int = {
    val leftRightClicks = jsArray.value.map { json =>
      val tappedButtonId = (json \ "TappedButtonId").as[String]
      (tappedButtonId == "TappedButtonLeft")
    }
    leftRightClicks.zip(leftRightClicks.tail).count{ case (prev, cur) => (prev != cur)}
  }

  private def createJsons: Future[Traversable[JsObject]] =
    for {
      // query the first tapping activity data set
      tappingItems1 <-
        getTappingItems(mPowerTappingDsa.dataSetRepo, mPowerTappingDsa.fieldRepo)

      // query the second tapping activity data set
      tappingItems2 <-
        getTappingItems(mPowerTapping2Dsa.dataSetRepo, mPowerTapping2Dsa.fieldRepo)

      // merge the tapping items
      tappingItems = tappingItems1 ++ tappingItems2

      clinicalJsons <- luxParkDataRepo.find(
        criteria = Seq(ClinicalField.MPowerId.toString #-> tappingItems.map(_.externalId).toSeq),
        projection = ClinicalField.values.map(_.toString)
      )

      Some(basicAssessmentStartDateField) <- luxParkFieldRepo.get(ClinicalField.BasicAssessmentStartDate.toString)

      Some(ageField) <- luxParkFieldRepo.get(ClinicalField.Age.toString)
    } yield {
      import NewMPowerTappingField._

      val basicAssessmentDateFieldType = ftf(basicAssessmentStartDateField.fieldTypeSpec).asValueOf[ju.Date]
      val ageFieldType = ftf(ageField.fieldTypeSpec).asValueOf[Double]

      val mPowerIdDataMap: Map[String, Traversable[(Option[ju.Date], Option[Double], JsObject)]] =
        clinicalJsons.map { clinicalJson =>
          val mPowerId = (clinicalJson \ ClinicalField.MPowerId.toString).get.as[String]
          val startDate = basicAssessmentDateFieldType.jsonToValue(clinicalJson \ ClinicalField.BasicAssessmentStartDate.toString)
          val age = ageFieldType.jsonToValue(clinicalJson \ ClinicalField.Age.toString)

          (mPowerId, (startDate, age, clinicalJson))
        }.groupBy(_._1).map{ case (key, keyJsons) => (key, keyJsons.map(_._2))}

      tappingItems.map { tappingItem =>
        mPowerIdDataMap.get(tappingItem.externalId).map { clinicalDateJsons =>
          val sortedDateClinicalJsons = clinicalDateJsons.filter(_._1.isDefined).map(x => (x._1.get, x._3)).toSeq.sortBy(_._1)

          val clinicalJson: Option[JsObject] = if (sortedDateClinicalJsons.nonEmpty) {
            val tappingCreatedOnDate = new ju.Date(tappingItem.createdOn)
            val indexAfter = sortedDateClinicalJsons.zipWithIndex.find(_._1._1.after(tappingCreatedOnDate)).map(_._2)
            val json = sortedDateClinicalJsons(indexAfter.map(i => Math.max(0, i-1)).getOrElse(sortedDateClinicalJsons.length - 1))._2
            Some(json)
          } else {
            clinicalDateJsons.headOption.map(_._3)
          }

          val ageJson: JsValue = clinicalDateJsons.find(_._2.isDefined).map(x => ageFieldType.valueToJson(x._2)).getOrElse(JsNull)

          clinicalJson.map( json =>
            json ++ Json.obj(
              ClinicalField.Age.toString -> ageJson,
              CreatedOn.toString -> tappingItem.createdOn,
              MedsMoment.toString -> tappingItem.medsMoment,
              LeftTappingCount.toString -> tappingItem.leftTappingCount,
              RightTappingCount.toString -> tappingItem.rightTappingCount,
              LeftTappingScore.toString -> tappingItem.leftTappingScore,
              RightTappingScore.toString -> tappingItem.rightTappingScore
            )
          )
        }
      }.flatten.flatten
    }

  private def getTappingItems(
    dataRepo: JsonCrudRepo,
    fieldRepo: FieldRepo
  ): Future[Traversable[MPowerTappingData]] =
    for {
      // get all JSONS
      tappingJsons <- dataRepo.find()

      // get the external id field
      externalIdField <- fieldRepo.get(MPowerTappingField.ExternalId.toString)
    } yield {
      val externalIdFieldType = ftf(externalIdField.get.fieldTypeSpec)

      tappingJsons.map { tappingJson =>
        def getValue[T: Reads](field: MPowerTappingField.Value): T =
          (tappingJson \ field.toString).asOpt[T].getOrElse(
              throw new IllegalArgumentException(s"'$field' is not of the expected type.")
          )

        import MPowerTappingField._

        val leftTappingJsons = getValue[JsArray](LeftTappingSamples)
        val rightTappingJsons = getValue[JsArray](RightTappingSamples)

        MPowerTappingData(
          externalId = externalIdFieldType.jsonToDisplayString(tappingJson \ ExternalId.toString),
          createdOn = getValue[Long](CreatedOn),
          medsMoment = getValue[Int](MedsMoment),
          leftTappingCount = leftTappingJsons.value.size,
          rightTappingCount = rightTappingJsons.value.size,
          leftTappingScore = countLeftRightAlternations(leftTappingJsons),
          rightTappingScore = countLeftRightAlternations(rightTappingJsons)
        )
      }
    }

  private def createFields: Future[Traversable[Field]] =
    for {
      tappingFields <-
        mPowerTappingDsa.fieldRepo.find(Seq("name" #-> MPowerTappingField.values.map(_.toString).toSeq))

      clinicalFields <-
        luxParkFieldRepo.find(Seq("name" #-> ClinicalField.values.map(_.toString).toSeq))

    } yield {

      import NewMPowerTappingField._
      val tappingNameFieldMap = tappingFields.map(field => (field.name, field)).toMap

      val createdOnField = tappingNameFieldMap.get(MPowerTappingField.CreatedOn.toString).get
      val medsMomentField = tappingNameFieldMap.get(MPowerTappingField.MedsMoment.toString).get

      clinicalFields ++ Seq(
        createdOnField.copy(name = CreatedOn.toString, label = Some("mPower Date Created")),
        medsMomentField.copy(name = MedsMoment.toString, label = Some("mPower Meds Moment in Day")),
        Field(LeftTappingCount.toString, Some("mPower Left Tapping Count"), FieldTypeId.Integer, false),
        Field(RightTappingCount.toString, Some("mPower Right Tapping Count"), FieldTypeId.Integer, false),
        Field(LeftTappingScore.toString, Some("mPower Left Tapping Score"), FieldTypeId.Integer, false),
        Field(RightTappingScore.toString, Some("mPower Right Tapping Score"), FieldTypeId.Integer, false)
      )
    }
}

object CreateClinicalMPowerTappingDataSet extends GuiceBuilderRunnable[CreateClinicalMPowerTappingDataSet] with App { run }