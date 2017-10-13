package runnables.luxpark

import javax.inject.Inject

import models.AdaException
import models.DataSetFormattersAndIds.JsObjectIdentity
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import runnables.FutureRunnable
import reactivemongo.play.json.BSONFormats._

import scala.concurrent.ExecutionContext.Implicits.global

class RemoveDuplicateClinicalVisits @Inject()(dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val logger = Logger // (this.getClass())

  private val dataSetId = "lux_park.clinical"

  private val subjectIdField = "cdisc_dm_usubjd"
  private val visitField = "redcap_event_name"
  private val idField = JsObjectIdentity.name

  private val keyFields = Seq(idField, subjectIdField, visitField)

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
            (
              (json \ subjectIdField).as[String],
              (json \ visitField).as[Int]
            ),
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