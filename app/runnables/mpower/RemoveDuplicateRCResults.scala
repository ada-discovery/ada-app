package runnables.mpower

import javax.inject.Inject

import dataaccess.RepoTypes.JsonCrudRepo
import models.AdaException
import models.DataSetFormattersAndIds.JsObjectIdentity
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import runnables.InputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class RemoveDuplicateRCResults @Inject()(
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[RemoveDuplicateRCResultsSpec] {

  private val logger = Logger // (this.getClass())

  private val dataSetFieldName = "inputOutputSpec-resultDataSetId"

  private val groupSize = 4

  private val idName = JsObjectIdentity.name

  override def runAsFuture(input: RemoveDuplicateRCResultsSpec) = {
    val resultsDsa = dsaf(input.dataSetId).getOrElse(
      throw new AdaException(s"Data set ${input.dataSetId} not found.")
    )

    for {
      // get the data set ids
      jsons <- resultsDsa.dataSetRepo.find(projection = Seq(dataSetFieldName))
      dataSetIds = jsons.map { json => (json \ dataSetFieldName).as[String] }.toSeq.sorted

      // collect all the record ids
      idDuplicates <- util.seqFutures(dataSetIds.grouped(groupSize)) { ids =>
        Future.sequence(ids.map { id =>
          logger.info(s"Finding duplicates in $id...")
          val dsa = dsaf(id).getOrElse(
            throw new AdaException(s"Data set ${id} not found.")
          )
          val repo = dsa.dataSetRepo

          for {
            // find duplicate records
            duplicateRecordIds <- findDuplicateRecordWithIds(repo)

            // delete the duplicates (if any)
            _ <-
              if (duplicateRecordIds.nonEmpty) {
                logger.info(id)
                logger.info("--------------------------------")
                val idsToRemove = duplicateRecordIds.flatMap { case (_, ids) => ids.tail}
                logger.info(s"Removing ${idsToRemove.size} records.")
                repo.delete(idsToRemove)
              } else
                Future(())
          } yield
            ()
        })
      }
    } yield
      ()
  }

  private def findDuplicateRecordWithIds(
    repo: JsonCrudRepo
  ): Future[Traversable[(String,  Traversable[BSONObjectID])]] =
    for {
      jsons <- repo.find(projection = Seq(idName, "recordId"))
    } yield {
      val recordIds = jsons.map { json =>
        ((json \ "recordId").as[String], (json \ idName).as[BSONObjectID])
      }
//      val duplicates = recordIds.toSeq.diff(recordIds.toSet.toSeq).toSet
      recordIds.groupBy(_._1).filter(_._2.size > 1).map { case (x, items) => (x, items.map(_._2)) }
    }

  override def inputType = typeOf[RemoveDuplicateRCResultsSpec]
}

case class RemoveDuplicateRCResultsSpec(dataSetId: String)