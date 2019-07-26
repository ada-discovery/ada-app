package runnables.other

import javax.inject.Inject
import models.{BatchOrderRequest, BatchRequestState}
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import play.api.{Configuration, Logger}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{RequestSettingRepo, BatchOrderRequestRepo}
import scala.concurrent.ExecutionContext.Implicits.global


class RequestRepoRun @Inject() (dsaf: DataSetAccessorFactory, configuration: Configuration, userRepo: UserRepo, committeeRepo: RequestSettingRepo, requestsRepo:BatchOrderRequestRepo)
  extends InputFutureRunnableExt[RequestRepoRunSpec] with RunnableHtmlOutput {
  private val logger = Logger

  override def runAsFuture(input: RequestRepoRunSpec) = {
    val requestId = Some(BSONObjectID.parse("577e18c24500004800cdc557").get)
    val sampleId = BSONObjectID.parse("577e18c24500004800cdc558").get
    val request = BatchOrderRequest(requestId,"dataSetId",Seq(sampleId),BatchRequestState.Created)
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