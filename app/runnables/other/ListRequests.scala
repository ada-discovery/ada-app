package runnables.other

import javax.inject.Inject
import org.incal.core.runnables.{FutureRunnable, RunnableHtmlOutput}
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo
import views.html.requests.list

import scala.concurrent.ExecutionContext.Implicits.global

class ListRequests @Inject()(requestsRepo: BatchOrderRequestRepo) extends FutureRunnable with RunnableHtmlOutput {

  override def runAsFuture =
    requestsRepo.find().map { requests =>
      // val page = Page(requests, 0, 0, requests.size,"")
      val html = list(requests.toSeq).toString()
      addDiv(html)
    }
}