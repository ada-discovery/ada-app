package runnables.core

import javax.inject.Inject

import play.api.Logger
import org.incal.core.InputFutureRunnable
import org.ada.server.AdaException
import persistence.dataset.DataSetAccessorFactory

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CompareFieldNames @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnable[CompareFieldNamesSpec] {

  private val logger = Logger // (this.getClass())

  override def runAsFuture(input: CompareFieldNamesSpec) =
    for {
      fieldNames <-
        Future.sequence(
          input.dataSetIds.map { dataSetId =>
            val dsa = dsaf(dataSetId).getOrElse(
              throw new AdaException(s"Data set id ${dataSetId} not found."))

            dsa.fieldRepo.find().map(_.map(_.name))
          }
        )
    } yield {
      val expectedCount = input.dataSetIds.size
      val unmatchedFieldNames = fieldNames.flatten.groupBy(identity).filter { case (x, items) => items.size != expectedCount }.map(_._1)

      logger.info("Unmatched field names found: " + unmatchedFieldNames.size)
      logger.info("-----------------")
      logger.info(unmatchedFieldNames.mkString(", ") + "\n")
    }

  override def inputType = typeOf[CompareFieldNamesSpec]
}

case class CompareFieldNamesSpec(dataSetIds: Seq[String])