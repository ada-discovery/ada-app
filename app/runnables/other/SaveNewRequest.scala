package runnables.other

import javax.inject.Inject
import models.{BatchOrderRequest, BatchRequestState}
import org.incal.core.runnables.{InputFutureRunnable, RunnableHtmlOutput}
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class SaveNewRequest @Inject() (requestsRepo:BatchOrderRequestRepo) extends InputFutureRunnable[SaveNewRequestSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: SaveNewRequestSpec) = {
   // val request = BatchOrderRequest(None, input.dataSetId, input.itemIds.flatMap(BSONObjectID.parse(_).toOption), BatchRequestState.Created)
   val request = BatchOrderRequest(None, input.dataSetId, "", BatchRequestState.Created)

    for {
      savedRequestId <- requestsRepo.save(request)
    } yield
      addParagraph(bold("New request save with id: " + savedRequestId))
  }

  override def inputType = typeOf[SaveNewRequestSpec]
}

case class SaveNewRequestSpec(
  dataSetId: String,
  itemIds: Seq[String]
)