package runnables.other

import javax.inject.Inject
import models.ApprovalCommittee
import org.incal.core.runnables.{InputFutureRunnable, RunnableHtmlOutput}
import services.BatchRequestRepoTypes.ApprovalCommitteeRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CommitteeRepoRun @Inject() (committeeRepo: ApprovalCommitteeRepo) extends InputFutureRunnable[CommitteeRepoRunSpec] with RunnableHtmlOutput {

  override def runAsFuture(input: CommitteeRepoRunSpec) = {
    val committee = ApprovalCommittee(None,input.fullName,input.email,input.institute)
    //    committeeRepo.find(Seq(EqualsCriterion(" _id", objectId)))
    committeeRepo.save(committee)

 for {
     committeeId <-  committeeRepo.save(committee)
     commiteeRead <- committeeRepo.get(committeeId)
    } yield {
      addParagraph(bold(commiteeRead.get.toString))
    }
  }

  override def inputType = typeOf[CommitteeRepoRunSpec]
}

case class CommitteeRepoRunSpec(
    fullName: String,
    email: String,
    institute: String
)