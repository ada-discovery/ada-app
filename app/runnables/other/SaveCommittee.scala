package runnables.other

import javax.inject.Inject
import models.ApprovalCommittee
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt, RunnableHtmlOutput}
import services.BatchOrderRequestRepoTypes.ApprovalCommitteeRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class SaveCommittee @Inject() (committeeRepo: ApprovalCommitteeRepo) extends InputFutureRunnableExt[ApprovalCommittee] with RunnableHtmlOutput {

  override def runAsFuture(committee: ApprovalCommittee) =
    for {
       committeeId <-  committeeRepo.save(committee)
       committeeLoaded <- committeeRepo.get(committeeId)
    } yield {
      addParagraph(bold(committeeLoaded.get.toString))
    }
}