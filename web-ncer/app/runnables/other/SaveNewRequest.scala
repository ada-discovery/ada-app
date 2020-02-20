package runnables.other

import javax.inject.Inject
import models.{BatchOrderRequest, BatchRequestState}
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt, RunnableHtmlOutput}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo

import scala.concurrent.ExecutionContext.Implicits.global

@Deprecated
class SaveNewRequest @Inject() (requestsRepo:BatchOrderRequestRepo) extends InputFutureRunnableExt[SaveNewRequestSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: SaveNewRequestSpec) = {
   // val request = BatchOrderRequest(None, input.dataSetId, input.itemIds.flatMap(BSONObjectID.parse(_).toOption), BatchRequestState.Draft)
   val request = BatchOrderRequest(None, input.dataSetId, Seq(), BatchRequestState.Draft, createdById = BSONObjectID.generate)

    for {
      savedRequestId <- requestsRepo.save(request)
    } yield
      addParagraph(bold("New request save with id: " + savedRequestId))
  }
}

case class SaveNewRequestSpec(
  dataSetId: String,
  itemIds: Seq[String]
)