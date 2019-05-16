package runnables.other

import javax.inject.Inject
import org.incal.core.runnables.{InputFutureRunnable, RunnableHtmlOutput}
import reactivemongo.bson.BSONObjectID
import services.BatchRequestRepoTypes.BatchRequestRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class GetRequestRun @Inject() (requestsRepo:BatchRequestRepo)
  extends InputFutureRunnable[GetRequestRunSpec] with RunnableHtmlOutput {
  override def runAsFuture(input: GetRequestRunSpec) = {

 for {
      requestRead <- requestsRepo.get(input.requestId)
    } yield {
      addParagraph(bold(requestRead.get.toString))
    }
  }

  override def inputType = typeOf[GetRequestRunSpec]
}

case class GetRequestRunSpec(
  requestId: BSONObjectID
)