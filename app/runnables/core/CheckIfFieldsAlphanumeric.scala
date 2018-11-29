package runnables.core

import javax.inject.Inject

import dataaccess.FieldRepoFactory
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import play.api.Logger
import org.incal.core.FutureRunnable
import util.hasNonAlphanumericUnderscore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CheckIfFieldsAlphanumeric @Inject() (
    fieldRepoFactory: FieldRepoFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends FutureRunnable {

  private val logger = Logger
  private val escapedDotString = "u002e"

  override def runAsFuture =
    for {
      dataSpaces <- dataSpaceMetaInfoRepo.find()

      results <- util.seqFutures(dataSpaces) { dataSpace =>
        val dataSetIds = dataSpace.dataSetMetaInfos.map(_.id)

        util.seqFutures(dataSetIds)(handleDataSet)
      }
    } yield {
      val filteredResults = results.flatten.filter(_._2.nonEmpty)
      logger.info(s"Found ${filteredResults.size} (out of ${results.flatten.size}) data sets with BAD fields:")

      filteredResults.map { case (dataSetId, fieldNames) =>
        val fieldNamesString = if (fieldNames.size > 3) fieldNames.take(3).mkString(", ") + "..." else fieldNames.mkString(", ")
        logger.info(s"Data set $dataSetId contains ${fieldNames.size} non-alpha fields: ${fieldNamesString}")
      }
    }

  private def handleDataSet(
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