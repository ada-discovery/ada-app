package runnables.luxpark

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global

import models.StorageType
import models.ml.{DataSetLinkSpec, DerivedDataSetSpec}
import org.incal.core.FutureRunnable
import services.DataSetService

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
    DerivedDataSetSpec(
      "harvard_ldopa.walking_data_w_scores",
      "Walking Data with Score",
      StorageType.Mongo
    ),
    Some(4),
    Some(1)
  )

  override def runAsFuture = dataSetService.linkDataSets(dataSetLinkSpec)
}
