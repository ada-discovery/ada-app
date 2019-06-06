package runnables.luxpark

import javax.inject.Inject

import scala.concurrent.ExecutionContext.Implicits.global
import org.ada.server.models.StorageType
import org.ada.server.models.datatrans.{DataSetLinkSpec, ResultDataSetSpec}
import org.incal.core.runnables.FutureRunnable
import org.ada.server.services.DataSetService

class LinkHarvardLdopaDataSets @Inject() (dataSetService: DataSetService) extends FutureRunnable {

  private val walkingFieldNames =
    Seq(
      "patient",
      "visit",
      "session",
      "task",
      "site",
      "dataset",
      "ROW_ID",
      "ROW_VERSION",
      "dataFileHandleId",
      "etag",
      "id",
      "limb",
      "modifiedOn",
      "name",
      "side"
    )

  private val scoreFieldNames =
    Seq(
      //      "patient",
      //      "visit",
      //      "session",
      //      "task",
      //      "site",
      //      "dataset",
      "bradykinesia_LeftUpperLimb",
      "bradykinesia_LowerLimbs",
      "bradykinesia_RightUpperLimb",
      "dyskinesia_LeftUpperLimb",
      "dyskinesia_LowerLimbs",
      "dyskinesia_RightUpperLimb",
      "tremor_LeftUpperLimb",
      "tremor_RightUpperLimb"
    )

  private val dataSetLinkSpec = DataSetLinkSpec(
    "harvard_ldopa.walking_data",
    "harvard_ldopa.scores",
    Seq("patient", "visit", "session", "task"),
    Seq("patient", "visit", "session", "task"),
    walkingFieldNames,
    scoreFieldNames,
    false,
    ResultDataSetSpec(
      "harvard_ldopa.walking_data_w_scores",
      "Walking Data with Score",
      StorageType.Mongo
    ),
    Some(4),
    Some(1)
  )

  override def runAsFuture = dataSetService.linkDataSets(dataSetLinkSpec)
}
