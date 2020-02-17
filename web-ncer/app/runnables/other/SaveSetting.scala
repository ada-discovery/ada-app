package runnables.other

import javax.inject.Inject
import models.BatchOrderRequestSetting
import org.incal.core.runnables.{InputFutureRunnableExt, RunnableHtmlOutput}
import services.BatchOrderRequestRepoTypes.BatchOrderRequestSettingRepo
import scala.concurrent.ExecutionContext.Implicits.global

@Deprecated
class SaveSetting @Inject() (
  settingRepo: BatchOrderRequestSettingRepo
) extends InputFutureRunnableExt[BatchOrderRequestSetting] with RunnableHtmlOutput {

  override def runAsFuture(setting: BatchOrderRequestSetting) =
    for {
       id <- settingRepo.save(setting)
       settingLoaded <- settingRepo.get(id)
    } yield
      settingLoaded.map { committee =>
        addParagraph(bold(committee.toString))
      }.getOrElse(
        addParagraph(bold("None found"))
      )
}