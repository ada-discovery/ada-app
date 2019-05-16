package runnables.other

import javax.inject.Inject
import org.incal.core.runnables.{FutureRunnable, RunnableHtmlOutput}
import services.BatchRequestRepoTypes.BatchRequestRepo

import scala.concurrent.ExecutionContext.Implicits.global

class GetRequestListRun @Inject()(requestsRepo: BatchRequestRepo)
  extends FutureRunnable with RunnableHtmlOutput {
  override def runAsFuture = {

    for {
      requestRead <- requestsRepo.find()
    } yield {
      addParagraph(bold(requestRead.toString))
    }
  }
}