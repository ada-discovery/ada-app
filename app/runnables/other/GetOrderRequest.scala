package runnables.other

import javax.inject.Inject
import org.incal.core.runnables.{InputFutureRunnable, RunnableHtmlOutput}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class GetOrderRequestRun @Inject() (requestsRepo:BatchOrderRequestRepo) extends InputFutureRunnable[GetRequestRunSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: GetRequestRunSpec) =
    requestsRepo.get(input.requestId).map(
      _.fold(
        addParagraph(bold(s"Request with id ${input.requestId} not found!"))
      )(
        request => addParagraph(bold(request.toString))
      ))

  override def inputType = typeOf[GetRequestRunSpec]
}

case class GetRequestRunSpec(
  requestId: BSONObjectID
)