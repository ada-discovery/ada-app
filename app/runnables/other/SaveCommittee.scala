package runnables.other

import javax.inject.Inject
import models.BatchOrderRequestSetting
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import services.BatchOrderRequestRepoTypes.BatchOrderRequestSettingRepo
import scala.concurrent.ExecutionContext.Implicits.global

class SaveCommittee @Inject() (committeeRepo: BatchOrderRequestSettingRepo) extends InputFutureRunnableExt[BatchOrderRequestSetting] with RunnableHtmlOutput {

  override def runAsFuture(committee: BatchOrderRequestSetting) =
    for {
       committeeId <-  committeeRepo.save(committee)
       committeeLoaded <- committeeRepo.get(committeeId)
    } yield {
      addParagraph(bold(committeeLoaded.get.toString))
    }
}