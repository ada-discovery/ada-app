package org.ada.web.runnables.core

import runnables.DsaInputFutureRunnable
import org.incal.core.runnables.RunnableHtmlOutput
import reactivemongo.bson.BSONObjectID
import scala.concurrent.ExecutionContext.Implicits.global

class DeleteItems extends DsaInputFutureRunnable[DeleteItemsSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: DeleteItemsSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      _ <- if (input.ids.size == 1)
        dsa.dataSetRepo.delete(input.ids.head)
      else
        dsa.dataSetRepo.delete(input.ids)
    } yield
      addParagraph(s"Deleted <b>${input.ids.size}</b> items:<br/>")
}

case class DeleteItemsSpec(
  dataSetId: String,
  ids: Seq[BSONObjectID]
)