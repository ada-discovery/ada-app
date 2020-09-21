package org.ada.server.runnables.core

import javax.inject.Inject
import play.api.Logger
import org.incal.core.runnables.InputFutureRunnableExt
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CompareFieldNames @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnableExt[CompareFieldNamesSpec] {

  private val logger = Logger

  override def runAsFuture(input: CompareFieldNamesSpec) =
    for {
      fieldNames <-
        Future.sequence(
          input.dataSetIds.map { dataSetId =>
            dsaf.getOrError(dataSetId).flatMap(dsa =>
              dsa.fieldRepo.find().map(_.map(_.name))
            )
          }
        )
    } yield {
      val expectedCount = input.dataSetIds.size
      val unmatchedFieldNames = fieldNames.flatten.groupBy(identity).filter { case (x, items) => items.size != expectedCount }.map(_._1)

      logger.info("Unmatched field names found: " + unmatchedFieldNames.size)
      logger.info("-----------------")
      logger.info(unmatchedFieldNames.mkString(", ") + "\n")
    }
}

case class CompareFieldNamesSpec(dataSetIds: Seq[String])