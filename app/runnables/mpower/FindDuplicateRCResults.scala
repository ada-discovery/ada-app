package runnables.mpower

import javax.inject.Inject

import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import org.incal.core.InputFutureRunnable
import org.incal.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class FindDuplicateRCResults @Inject()(
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[FindDuplicateRCResultsSpec] {

  private val logger = Logger // (this.getClass())

  private val dataSetFieldName = "inputOutputSpec-resultDataSetId"

  private val groupSize = 4

  override def runAsFuture(input: FindDuplicateRCResultsSpec) = {
    val dsa = dsaf(input.dataSetId).getOrElse(
      throw new AdaException(s"Data set ${input.dataSetId} not found.")
    )

    for {
      // get the data set ids
      jsons <- dsa.dataSetRepo.find(projection = Seq(dataSetFieldName))
      dataSetIds = jsons.map { json => (json \ dataSetFieldName).as[String] }.toSeq.sorted

      // collect all the record ids
      idDuplicates <- seqFutures(dataSetIds.grouped(groupSize)) { ids =>
        Future.sequence(ids.map { id =>
          findDuplicateRecordIds(id).map((id, _))
        })
      }
    } yield {
      logger.info("Duplicates found:")
      logger.info("-----------------")
      idDuplicates.flatten.filter(_._2.nonEmpty).foreach { case (dataSetId, duplicates) =>
        logger.info(dataSetId + " : " + duplicates.size)
        logger.info(duplicates.mkString(", ") + "\n")
      }
    }
  }

  private def findDuplicateRecordIds(dataSetId: String): Future[Traversable[String]] = {
    logger.info(s"Finding duplicates in $dataSetId...")

    val dsa = dsaf(dataSetId).getOrElse(
      throw new AdaException(s"Data set ${dataSetId} not found.")
    )

    for {
      jsons <- dsa.dataSetRepo.find(projection = Seq("recordId"))
    } yield {
      val recordIds = jsons.map(json => (json \ "recordId").as[String])
//      val duplicates = recordIds.toSeq.diff(recordIds.toSet.toSeq).toSet
      recordIds.toSeq.groupBy(identity).collect { case (x, Seq(_,_,_*)) => x }
    }
  }

  override def inputType = typeOf[FindDuplicateRCResultsSpec]
}

case class FindDuplicateRCResultsSpec(dataSetId: String)