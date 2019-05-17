package runnables.other

import javax.inject.Inject
import org.incal.core.runnables.{FutureRunnable, RunnableHtmlOutput}
import services.BatchRequestRepoTypes.BatchRequestRepo
import views.html.requests.list

import scala.concurrent.ExecutionContext.Implicits.global

class GetRequestListRun @Inject()(requestsRepo: BatchRequestRepo)
  extends FutureRunnable with RunnableHtmlOutput {
  override def runAsFuture = {

    for {
      requestRead <- requestsRepo.find()
    } yield {


//      val requests: Seq[BSONObjectID]=requestRead.map(request=>request._id.get).toSeq


    // val page = Page(requests, 0, 0, requests.size,"")
      val html = list(requestRead.toSeq).toString()
      addDiv(html)
     // addParagraph(bold(requestRead.toString))
    }
  }
}