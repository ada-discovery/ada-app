package runnables.mpower

import javax.inject.Inject

import org.ada.server.dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import org.incal.core.runnables.InputFutureRunnable
import org.incal.core.util.seqFutures

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class ImportMPowerIndividualFeatureScoreForDataSpace @Inject()(
    importScores: ImportMPowerIndividualFeatureScores,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends InputFutureRunnable[ImportMPowerIndividualFeatureScoreForDataSpaceSpec] {

  private val logger = Logger

  override def runAsFuture(
    input: ImportMPowerIndividualFeatureScoreForDataSpaceSpec
  ) =
    for {
      // find a data space
      dataSpace <- dataSpaceMetaInfoRepo.get(input.dataSpaceId)

      // collect all the subordinate data sets
      dataSetIdNames = dataSpace.map(_.dataSetMetaInfos.map(info => (info.id, info.name))).getOrElse(Nil)

      // import individual feature scores one by one
      _ <- seqFutures(dataSetIdNames) { case (dataSetId, dataSetName) =>
        logger.info(s"Import individual feature scores for the data set $dataSetId.")
        importScores.runAsFuture(ImportMPowerIndividualFeatureScoresSpec(
          dataSetId,
          dataSetId + "_ext",
          dataSetName + " (Ext)",
          input.fileName,
          input.processingBatchSize
        ))
      }
    } yield
      ()

  override def inputType = typeOf[ImportMPowerIndividualFeatureScoreForDataSpaceSpec]
}

case class ImportMPowerIndividualFeatureScoreForDataSpaceSpec(
  dataSpaceId: BSONObjectID,
  fileName: String,
  processingBatchSize: Int
)