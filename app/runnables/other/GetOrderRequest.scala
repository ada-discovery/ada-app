package runnables.other

import javax.inject.Inject
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt, RunnableHtmlOutput}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo

import scala.concurrent.ExecutionContext.Implicits.global

@Deprecated
class GetOrderRequestRun @Inject() (requestRepo: BatchOrderRequestRepo) extends InputFutureRunnableExt[GetOrderRequestRunSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: GetOrderRequestRunSpec) =
    requestRepo.get(input.requestId).map(
      _.fold(
        addParagraph(bold(s"Request with id ${input.requestId} not found!"))
      )(
        request => addParagraph(bold(request.toString))
      )
    )
}

case class GetOrderRequestRunSpec(
  requestId: BSONObjectID
)