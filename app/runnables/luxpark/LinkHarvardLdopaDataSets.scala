package runnables.luxpark

import javax.inject.Inject
import org.ada.server.dataaccess.StreamSpec
import org.ada.server.models.StorageType
import org.ada.server.models.datatrans.{LinkTwoDataSetsTransformation, ResultDataSetSpec}
import org.incal.core.runnables.FutureRunnable
import org.ada.server.services.ServiceTypes.DataSetCentralTransformer

class LinkHarvardLdopaDataSets @Inject()(centralTransformer: DataSetCentralTransformer) extends FutureRunnable {

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

  private val dataSetLinkSpec = LinkTwoDataSetsTransformation(
    None,
    "harvard_ldopa.walking_data",
    "harvard_ldopa.scores",
    Seq(
      ("patient", "patient"),
      ("visit", "visit"),
      ("session", "session"),
      ("task", "task")
    ),
    walkingFieldNames,
    scoreFieldNames,
    false,
    ResultDataSetSpec(
      "harvard_ldopa.walking_data_w_scores",
      "Walking Data with Score",
      StorageType.Mongo
    ),
    StreamSpec(batchSize = Some(4))
  )

  override def runAsFuture = centralTransformer(dataSetLinkSpec)
}
