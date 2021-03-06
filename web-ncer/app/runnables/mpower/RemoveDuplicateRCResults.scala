package runnables.mpower

import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.JsonCrudRepo
import org.ada.server.AdaException
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.incal.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class RemoveDuplicateRCResults @Inject()(
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnableExt[RemoveDuplicateRCResultsSpec] {

  private val logger = Logger // (this.getClass())

  private val dataSetFieldName = "inputOutputSpec-resultDataSetId"

  private val groupSize = 4

  private val idName = JsObjectIdentity.name

  override def runAsFuture(input: RemoveDuplicateRCResultsSpec) =
    for {
      // data set accessor
      resultsDsa <- dsaf.getOrError(input.dataSetId)

      // get the data set ids
      jsons <- resultsDsa.dataSetRepo.find(projection = Seq(dataSetFieldName))
      dataSetIds = jsons.map { json => (json \ dataSetFieldName).as[String] }.toSeq.sorted

      // collect all the record ids
      idDuplicates <- seqFutures(dataSetIds.grouped(groupSize)) { ids =>
        Future.sequence(ids.map { id =>
          logger.info(s"Finding duplicates in $id...")

          for {
            // data set accessor
            dsa <- dsaf.getOrError(id)
            repo = dsa.dataSetRepo

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
}

case class RemoveDuplicateRCResultsSpec(dataSetId: String)