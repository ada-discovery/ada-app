package runnables.core

import javax.inject.Inject

import org.ada.server.dataaccess.dataset.FieldRepoFactory
import org.ada.server.dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import org.ada.server.models.DataSpaceMetaInfo
import org.ada.server.AdaException
import play.api.Logger
import org.incal.core.runnables.{FutureRunnable, InputFutureRunnable}
import org.incal.core.util.{seqFutures, hasNonAlphanumericUnderscore}
import reactivemongo.bson.BSONObjectID
import org.ada.web.services.DataSpaceService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class CheckIfFieldsAlphanumericForAll @Inject() (
    val fieldRepoFactory: FieldRepoFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends FutureRunnable with CheckIfFieldsAlphanumericHelper{

  override def runAsFuture =
    for {
      dataSpaces <- dataSpaceMetaInfoRepo.find()

      results <- seqFutures(dataSpaces) { dataSpace =>
        val dataSetIds = dataSpace.dataSetMetaInfos.map(_.id)

        seqFutures(dataSetIds)(checkDataSet)
      }
    } yield {
      val filteredResults = results.flatten.filter(_._2.nonEmpty)
      logger.info(s"Found ${filteredResults.size} (out of ${results.flatten.size}) data sets with BAD fields:")

      filteredResults.foreach { case (dataSetId, fieldNames) =>
        val fieldNamesString = if (fieldNames.size > 3) fieldNames.take(3).mkString(", ") + "..." else fieldNames.mkString(", ")
        logger.info(s"Data set $dataSetId contains ${fieldNames.size} non-alpha fields: ${fieldNamesString}")
      }
    }
}

class CheckIfFieldsAlphanumericForDataSpaceRecursively @Inject() (
  val fieldRepoFactory: FieldRepoFactory,
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
  dataSpaceService: DataSpaceService
) extends InputFutureRunnable[CheckIfFieldsAlphanumericForDataSpaceRecursivelySpec] with CheckIfFieldsAlphanumericHelper{

  override def runAsFuture(input: CheckIfFieldsAlphanumericForDataSpaceRecursivelySpec) =
    for {
      rootDataSpaces <- dataSpaceService.allAsTree

      dataSpace = rootDataSpaces.map(dataSpaceService.findRecursively(input.dataSpaceId, _)).find(_.isDefined).flatten

      results <- checkDataSpaceRecursively(dataSpace.getOrElse(
        throw new AdaException(s"Data space ${input.dataSpaceId} not found.")
      ))
    } yield {
      val filteredResults = results.filter(_._2.nonEmpty)
      logger.info(s"Found ${filteredResults.size} (out of ${results.size}) data sets with BAD fields:")

      filteredResults.foreach { case (dataSetId, fieldNames) =>
        val fieldNamesString = if (fieldNames.size > 3) fieldNames.take(3).mkString(", ") + "..." else fieldNames.mkString(", ")
        logger.info(s"Data set $dataSetId contains ${fieldNames.size} non-alpha fields: ${fieldNamesString}")
      }
    }

  private def checkDataSpaceRecursively(
    dataSpace: DataSpaceMetaInfo
  ): Future[Traversable[(String, Traversable[String])]] = {
    val dataSetIds = dataSpace.dataSetMetaInfos.map(_.id)

    for {
      results <- seqFutures(dataSetIds)(checkDataSet)
      subResults <- seqFutures(dataSpace.children)(checkDataSpaceRecursively)
    } yield
      results ++ subResults.flatten
  }

  override def inputType = typeOf[CheckIfFieldsAlphanumericForDataSpaceRecursivelySpec]
}

case class CheckIfFieldsAlphanumericForDataSpaceRecursivelySpec(
  dataSpaceId: BSONObjectID
)

trait CheckIfFieldsAlphanumericHelper {

  protected val logger = Logger
  protected val escapedDotString = "u002e"
  val fieldRepoFactory: FieldRepoFactory

  protected def checkDataSet(
    dataSetId: String
  ): Future[(String, Traversable[String])] = {
    logger.info(s"Checking the fields of the data set $dataSetId.")

    val fieldRepo = fieldRepoFactory(dataSetId)

    for {
      fields <- fieldRepo.find()
    } yield {
      val fieldNames = fields.map(_.name).filter { name =>
        hasNonAlphanumericUnderscore(name) || name.contains(escapedDotString)
      }
      (dataSetId, fieldNames)
    }
  }
}