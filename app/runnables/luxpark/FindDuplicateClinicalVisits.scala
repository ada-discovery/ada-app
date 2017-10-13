package runnables.luxpark

import javax.inject.Inject

import models.AdaException
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import runnables.FutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global

class FindDuplicateClinicalVisits @Inject()(dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val logger = Logger // (this.getClass())

  private val dataSetId = "lux_park.clinical"

  private val subjectIdField = "cdisc_dm_usubjd"
  private val visitField = "redcap_event_name"
  private val keyFields = Seq(subjectIdField, visitField)

  override def runAsFuture = {
    val dsa = dsaf(dataSetId).getOrElse(
      throw new AdaException(s"Data set ${dataSetId} not found.")
    )

    for {
      jsons <- dsa.dataSetRepo.find(projection = keyFields)
    } yield {
      val subjectIdVisits = jsons.map { json =>
        ((json \ subjectIdField).as[String], (json \ visitField).as[Int])
      }

      val  duplicates = subjectIdVisits.toSeq.groupBy(identity).collect { case (x, Seq(_,_,_*)) => x }
      logger.info("Duplicates found: " + duplicates.size)
      logger.info("-----------------")
      logger.info(duplicates.mkString(", ") + "\n")
    }
  }
}