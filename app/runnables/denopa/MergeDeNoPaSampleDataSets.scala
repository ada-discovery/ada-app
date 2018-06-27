package runnables.denopa

import javax.inject.Inject

import models.StorageType
import models.ml.ResultDataSetSpec
import runnables.{FutureRunnable, GuiceBuilderRunnable}
import services.DataSetService

class MergeDeNoPaSampleDataSets @Inject() (
    dataSetService: DataSetService
  ) extends FutureRunnable {

  private val mergedDataSetId = "denopa.sample"
  private val mergedDataSetName = "Sample"

  private val dataSetIds = Seq(
    "denopa.baseline_sample",
    "denopa.first_visit_sample",
    "denopa.second_visit_sample"
  )

  private val fieldNameMappings = Seq(
    Seq("Box_ID", "Box_ID", "Box_ID"),
    Seq("Box_Position", "Box_Position", "Box_Position"),
    Seq("IBBL_ID", "IBBL_ID", "IBBL_ID"),
    Seq("Subject_ID", "Subject_ID", "Subject_ID"),
    Seq("Visit", "Visit", "Visit"),
    Seq("Visit_ID", "Visit_ID", "Visit_ID")
  )

  override def runAsFuture =
    dataSetService.mergeDataSets(
      ResultDataSetSpec(
        mergedDataSetId,
        mergedDataSetName,
        StorageType.ElasticSearch
      ),
      dataSetIds,
      fieldNameMappings
    )
}

object MergeDeNoPaSampleDataSets extends GuiceBuilderRunnable[MergeDeNoPaSampleDataSets] with App { run }