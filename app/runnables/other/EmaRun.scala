package runnables.other

import javax.inject.Inject
import models.{ApprovalCommittee, BatchRequest, BatchRequestState}
import org.ada.server.dataaccess.JsonReadonlyRepoExtra._
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.models.FieldTypeId
import org.incal.core.runnables.InputFutureRunnable
import org.incal.core.dataaccess.Criterion.Infix
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.dataaccess.EqualsCriterion
import play.api.{Configuration, Logger}
import reactivemongo.bson.BSONObjectID
import services.BatchRequestRepoTypes.{ApprovalCommitteeRepo, BatchRequestRepo}

import scala.concurrent.{Await, duration}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.reflect.runtime.universe.typeOf
import scala.util.parsing.json.JSON

class EmaRun @Inject() (dsaf: DataSetAccessorFactory, configuration: Configuration, userRepo: UserRepo, committeeRepo: ApprovalCommitteeRepo, requestsRepo:BatchRequestRepo) extends InputFutureRunnable[EmaRunRunSpec] {
  private val logger = Logger

  override def runAsFuture(input: EmaRunRunSpec) = {
    val dsa = dsaf(input.dataSetId).getOrElse(
      throw new IllegalArgumentException(s"Data set ${input.dataSetId} not found")
    )

    val requestId = Some(BSONObjectID.parse("577e18c24500004800cdc557").get)
    val sampleId = BSONObjectID.parse("577e18c24500004800cdc558").get
    val request = BatchRequest(requestId,"dataSetId",Seq(sampleId),BatchRequestState.Created)
    requestsRepo.delete(requestId)
    requestsRepo.save(request)

    implicit val committeeId = Some(BSONObjectID.parse("577e18c24500004800cdc557").get)
    val committee = ApprovalCommittee(committeeId,"dataSetId","full name","institute")
    //    committeeRepo.find(Seq(EqualsCriterion(" _id", objectId)))
    committeeRepo.delete(committeeId)
    committeeRepo.save(committee)

    val commiteeRead = Await.result(committeeRepo.get(committeeId.get), DurationInt(10).seconds)
    val repoRead = Await.result(requestsRepo.get(requestId.get), DurationInt(10).seconds)
    logger.info("committee "+ commiteeRead.get.toString)
    logger.info("request "+ repoRead.get.toString)


    val projectName = configuration.getString("project.name").getOrElse("N/A")

    for {
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

      logger.info(s"Item/field count: $count/$fieldCount")

      val doubleFieldNamesString = doubleFields.map(_.name).mkString(", ")
      logger.info(s"Double fields: $doubleFieldNamesString")

      logger.info(s"Min/max value of ${input.fieldName}: $minValue/$maxValue.")

      logger.info("User's name (if found): " + user.map(_.ldapDn).getOrElse("N/A"))
    }
  }

  override def inputType = typeOf[EmaRunRunSpec]
}

case class EmaRunRunSpec(
  dataSetId: String,
  fieldName: String,
  email: String
)