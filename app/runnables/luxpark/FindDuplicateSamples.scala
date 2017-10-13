package runnables.luxpark

import javax.inject.Inject

import models.AdaException
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import runnables.FutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global

class FindDuplicateSamples @Inject()(dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val logger = Logger // (this.getClass())

  private val dataSetId = "lux_park.plate_sample_with_subject_oct_17"

  private val sampleIdField = "SampleId"
  private val keyFields = Seq(sampleIdField)

  override def runAsFuture = {
    val dsa = dsaf(dataSetId).getOrElse(
      throw new AdaException(s"Data set ${dataSetId} not found.")
    )

    for {
      jsons <- dsa.dataSetRepo.find(projection = keyFields)
    } yield {
      val subjectIdVisits = jsons.map { json => (json \ sampleIdField).as[String]}

      val  duplicates = subjectIdVisits.toSeq.groupBy(identity).collect { case (x, Seq(_,_,_*)) => x }
      logger.info("Duplicates found: " + duplicates.size)
      logger.info("-----------------")
      logger.info(duplicates.mkString(", ") + "\n")
    }
  }
}