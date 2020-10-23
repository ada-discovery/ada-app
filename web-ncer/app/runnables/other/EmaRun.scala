package runnables.other

import java.util.Date

import javax.inject.Inject
import models.{BatchOrderRequest, BatchOrderRequestSetting, BatchRequestState}
import org.ada.server.dataaccess.JsonReadonlyRepoExtra._
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.FieldTypeId
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.core.dataaccess.EqualsCriterion
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import play.api.{Configuration, Logger}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{BatchOrderRequestRepo, BatchOrderRequestSettingRepo}

import scala.concurrent.ExecutionContext.Implicits.global

@Deprecated
class EmaRun @Inject() (
  dsaf: DataSetAccessorFactory,
  configuration: Configuration,
  userRepo: UserRepo,
  settingRepo: BatchOrderRequestSettingRepo,
  requestRepo: BatchOrderRequestRepo
) extends InputFutureRunnableExt[EmaRunRunSpec] with RunnableHtmlOutput {
  private val logger = Logger

  override def runAsFuture(input: EmaRunRunSpec) = {
    val dsa = dsaf.applySync(input.dataSetId).getOrElse(
      throw new IllegalArgumentException(s"Data set ${input.dataSetId} not found")
    )

    val requestId = Some(BSONObjectID.parse("577e18c24500004800cdc557").get)
    val sampleId = BSONObjectID.parse("577e18c24500004800cdc558").get
    val request = BatchOrderRequest(requestId,"dataSetId", Seq(sampleId), BatchRequestState.Created, createdById = BSONObjectID.generate)
    requestRepo.delete(requestId)
    requestRepo.save(request)

    val committeeId = BSONObjectID.parse("577e18c24500004800cdc557").toOption
    val committee = BatchOrderRequestSetting(committeeId, "dataSetId", new Date(), Nil, BSONObjectID.generate())
    settingRepo.delete(committeeId)
    settingRepo.save(committee)

    val projectName = configuration.getString("project.name").getOrElse("N/A")

    for {
      commiteeRead <- settingRepo.get(committeeId.get)
      repoRead <- requestRepo.get(requestId.get)
      // total count
      count <- dsa.dataSetRepo.count()

      // total field count
      fieldCount <- dsa.fieldRepo.count()

      // retrieve all the double fields
      doubleFields <- dsa.fieldRepo.find(Seq("fieldType" #== FieldTypeId.Double))

      // get the min value of a given field
      minValue <- dsa.dataSetRepo.min(input.fieldName, addNotNullCriterion = true)

      // get the max value of a given field
      maxValue <- dsa.dataSetRepo.max(input.fieldName, addNotNullCriterion = true)

      user1 <- userRepo.find(Seq(EqualsCriterion("ldapDn", input.email))).map(_.headOption)
      user <- userRepo.find(Seq("ldapDn" #== input.email)).map(_.headOption)
    } yield {

      logger.info("committee "+ commiteeRead.get.toString)
      logger.info("request "+ repoRead.get.toString)


      logger.info(s"Item/field count: $count/$fieldCount")

      val doubleFieldNamesString = doubleFields.map(_.name).mkString(", ")
      logger.info(s"Double fields: $doubleFieldNamesString")

      logger.info(s"Min/max value of ${input.fieldName}: $minValue/$maxValue.")

      logger.info("User's name (if found): " + user.map(_.ldapDn).getOrElse("N/A"))

      addParagraph(bold("Hooray"))
    }
  }
}

case class EmaRunRunSpec(
  dataSetId: String,
  fieldName: String,
  email: String
)