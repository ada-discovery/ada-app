package runnables.other

import javax.inject.Inject
import models.BatchRequestSetting
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import services.BatchOrderRequestRepoTypes.RequestSettingRepo
import scala.concurrent.ExecutionContext.Implicits.global

class SaveCommittee @Inject() (committeeRepo: RequestSettingRepo) extends InputFutureRunnableExt[BatchRequestSetting] with RunnableHtmlOutput {

  override def runAsFuture(committee: BatchRequestSetting) =
    for {
       committeeId <-  committeeRepo.save(committee)
       committeeLoaded <- committeeRepo.get(committeeId)
    } yield {
      addParagraph(bold(committeeLoaded.get.toString))
    }
}