package org.ada.web.runnables.core

import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.DictionaryRootRepo
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt, RunnableHtmlOutput}
import org.incal.core.dataaccess.Criterion._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RemoveDictionary @Inject()(
  dictionaryRootRepo: DictionaryRootRepo
) extends InputFutureRunnableExt[RemoveDictionarySpec] with RunnableHtmlOutput {

  override def runAsFuture(input: RemoveDictionarySpec): Future[Unit] = {
    for {
      // find a dictionary for a given data set id
      dictionary <- dictionaryRootRepo.find(Seq("dataSetId" #== input.dataSetId), limit = Some(1)).map(_.headOption)

      // delete if found
      _ <- if (dictionary.isDefined)
        dictionaryRootRepo.delete(dictionary.get._id.get).map(_ =>
          addParagraph(s"Dictionary for the data set id ${bold(input.dataSetId)} deleted.")
        )
      else
        Future(
          addParagraph(s"Dictionary for the data set id ${bold(input.dataSetId)} NOT FOUND.")
        )
    } yield
      ()
  }
}

case class RemoveDictionarySpec(
  dataSetId: String
)