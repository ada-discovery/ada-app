package runnables.denopa

import javax.inject.Inject

import org.ada.server.models.StorageType
import models.DerivedDataSetSpec
import org.incal.core.FutureRunnable
import org.incal.play.GuiceRunnableApp
import services.DataSetService

class MergeDeNoPaSampleDataSets @Inject() (dataSetService: DataSetService) extends FutureRunnable {

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
      DerivedDataSetSpec(
        mergedDataSetId,
        mergedDataSetName,
        StorageType.ElasticSearch
      ),
      dataSetIds,
      fieldNameMappings
    )
}

object MergeDeNoPaSampleDataSets extends GuiceRunnableApp[MergeDeNoPaSampleDataSets]