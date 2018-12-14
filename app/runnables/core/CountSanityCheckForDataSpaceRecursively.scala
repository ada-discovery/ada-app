package runnables.core

import javax.inject.Inject

import org.incal.core.InputFutureRunnable
import org.incal.core.util.seqFutures
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import dataaccess.JsonReadonlyRepoExtra._
import models.{AdaException, DataSpaceMetaInfo}
import reactivemongo.bson.BSONObjectID
import services.DataSpaceService

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CountSanityCheckForDataSpaceRecursively @Inject() (
    val dsaf: DataSetAccessorFactory,
    dataSpaceService: DataSpaceService
  ) extends InputFutureRunnable[CountSanityCheckForDataSpaceRecursivelySpec] with CountSanityCheckHelper {

  override def runAsFuture(input: CountSanityCheckForDataSpaceRecursivelySpec) =
    for {
      dataSpaces <- dataSpaceService.allAsTree

      dataSpace = dataSpaces.map(dataSpaceService.findRecursively(input.dataSpaceId, _)).find(_.isDefined).flatten

      results <- checkDataSpaceRecursively(dataSpace.getOrElse(
        throw new AdaException(s"Data space ${input.dataSpaceId} not found.")
      ))
    } yield {
      val filteredCounts = results.filter { case (_, count1, count2) => count1 != count2 }
      logger.info(s"Found ${filteredCounts.size} (out of ${results.size}) data sets with BAD counts:")

      filteredCounts.foreach { case (dataSetId, count1, count2) =>
        logger.info(s"Data set $dataSetId has a wrong count $count1 vs $count2 (# ids).")
      }
    }

  private def checkDataSpaceRecursively(
    dataSpace: DataSpaceMetaInfo
  ): Future[Traversable[(String, Int, Int)]] = {
    val dataSetIds = dataSpace.dataSetMetaInfos.map(_.id)

    for {
      results <- seqFutures(dataSetIds)(checkDataSet)
      subResults <- seqFutures(dataSpace.children)(checkDataSpaceRecursively)
    } yield
      results ++ subResults.flatten
  }

  override def inputType = typeOf[CountSanityCheckForDataSpaceRecursivelySpec]
}

class CountSanityCheckForDataSet @Inject() (
  val dsaf: DataSetAccessorFactory
) extends InputFutureRunnable[CountSanityCheckForDataSetSpec] with CountSanityCheckHelper {

  override def runAsFuture(
    input: CountSanityCheckForDataSetSpec
  ) =
    for {
      (_, count1, count2) <- checkDataSet(input.dataSetId)
    } yield
      if (count1 != count2) {
        logger.warn(s"Data set ${input.dataSetId} has a wrong count $count1 vs $count2 (# ids).")
      } else {
        logger.info(s"Data set ${input.dataSetId} passed a sanity count check.")
      }

  override def inputType = typeOf[CountSanityCheckForDataSetSpec]
}

trait CountSanityCheckHelper {

  protected val dsaf: DataSetAccessorFactory

  protected val logger = Logger

  protected def checkDataSet(
    dataSetId: String
  ): Future[(String, Int, Int)] = {
    logger.info(s"Checking the count for the data set $dataSetId.")

    val dsa = dsaf(dataSetId).get

    for {
      count <- dsa.dataSetRepo.count()
      ids <- dsa.dataSetRepo.allIds
    } yield
      (dataSetId, count, ids.size)
  }
}
case class CountSanityCheckForDataSpaceRecursivelySpec(
  dataSpaceId: BSONObjectID
)

case class CountSanityCheckForDataSetSpec(
  dataSetId: String
)