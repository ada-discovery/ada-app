package runnables.other

import javax.inject.Inject
import models.{BatchRequest, BatchRequestState}
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.runnables.{InputFutureRunnable, RunnableHtmlOutput}
import play.api.{Configuration, Logger}
import reactivemongo.bson.BSONObjectID
import services.BatchRequestRepoTypes.{ApprovalCommitteeRepo, BatchRequestRepo}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class RequestRepoRun @Inject() (dsaf: DataSetAccessorFactory, configuration: Configuration, userRepo: UserRepo, committeeRepo: ApprovalCommitteeRepo, requestsRepo:BatchRequestRepo)
  extends InputFutureRunnable[RequestRepoRunSpec] with RunnableHtmlOutput {
  private val logger = Logger

  override def runAsFuture(input: RequestRepoRunSpec) = {


    val requestId = Some(BSONObjectID.parse("577e18c24500004800cdc557").get)
    val sampleId = BSONObjectID.parse("577e18c24500004800cdc558").get
    val request = BatchRequest(requestId,"dataSetId",Seq(sampleId),BatchRequestState.Created)
    //requestsRepo.delete(requestId)
    requestsRepo.save(request)


 for {
      requestRead <- committeeRepo.get(requestId.get)
    } yield {

   requestRead


      addParagraph(bold(requestRead.get.toString))
    }
  }

  override def inputType = typeOf[RequestRepoRunSpec]
}

case class RequestRepoRunSpec(
  dataSetId: String,
  fieldName: String,
  email: String
)