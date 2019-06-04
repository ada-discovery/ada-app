package runnables.other

import javax.inject.Inject
import models.{BatchOrderRequest, BatchRequestState}
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt, RunnableHtmlOutput}
import play.api.{Configuration, Logger}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class RequestRepoRun @Inject() (dsaf: DataSetAccessorFactory, configuration: Configuration, userRepo: UserRepo, committeeRepo: ApprovalCommitteeRepo, requestsRepo:BatchOrderRequestRepo)
  extends InputFutureRunnableExt[RequestRepoRunSpec] with RunnableHtmlOutput {
  private val logger = Logger

  override def runAsFuture(input: RequestRepoRunSpec) = {


    val requestId = Some(BSONObjectID.parse("577e18c24500004800cdc557").get)
    val sampleId = BSONObjectID.parse("577e18c24500004800cdc558").get
    val request = BatchOrderRequest(requestId,"dataSetId","577e18c24500004800cdc558",BatchRequestState.Created)
    //requestsRepo.delete(requestId)
    requestsRepo.save(request)


 for {
      requestRead <- committeeRepo.get(requestId.get)
    } yield {

   requestRead


      addParagraph(bold(requestRead.get.toString))
    }
  }
}

case class RequestRepoRunSpec(
  dataSetId: String,
  fieldName: String,
  email: String
)