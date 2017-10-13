package runnables.luxpark

import javax.inject.Inject

import models.AdaException
import models.DataSetFormattersAndIds.JsObjectIdentity
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import runnables.FutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global

class RemoveDuplicateSamples @Inject()(dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val logger = Logger // (this.getClass())

  private val dataSetId = "lux_park.plate_sample_with_subject_oct_17"

  private val sampleIdField = "SampleId"
  private val idField = JsObjectIdentity.name
  private val keyFields = Seq(idField, sampleIdField)

  override def runAsFuture = {
    val dsa = dsaf(dataSetId).getOrElse(
      throw new AdaException(s"Data set ${dataSetId} not found.")
    )

    for {
      jsons <- dsa.dataSetRepo.find(projection = keyFields)

      // delete ids
      _ <- {
        val subjectIdVisitIds = jsons.map { json =>
          (
            (json \ sampleIdField).as[String],
            (json \ idField).as[BSONObjectID]
          )
        }

        val idsToRemove = subjectIdVisitIds.groupBy(_._1).filter(_._2.size > 1).flatMap { case (_, items) => items.map(_._2).tail }

        logger.info(s"Removing ${idsToRemove.size } duplicates")
        dsa.dataSetRepo.delete(idsToRemove)
      }
    } yield
      ()
  }
}