package runnables.other

import javax.inject.Inject
import models.{BatchRequest, BatchRequestState}
import org.incal.core.runnables.{InputFutureRunnable, RunnableHtmlOutput}
import reactivemongo.bson.BSONObjectID
import services.BatchRequestRepoTypes.BatchRequestRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class PostRequestRun @Inject() (requestsRepo:BatchRequestRepo)
  extends InputFutureRunnable[PostRequestRunSpec] with RunnableHtmlOutput {
  override def runAsFuture(input: PostRequestRunSpec) = {
    val request = BatchRequest(None,input.dataSetId,input.itemIds,BatchRequestState.Created)
 for {
      savedRequestId <- requestsRepo.save(request)
    } yield {
      addParagraph(bold("saved new request with id: " + savedRequestId))
    }
  }

  override def inputType = typeOf[PostRequestRunSpec]
}

case class PostRequestRunSpec(
    dataSetId: String,
    itemIds: Seq[BSONObjectID]
)