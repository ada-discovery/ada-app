package runnables.ppmi

import javax.inject.Inject
import org.incal.core.dataaccess.StreamSpec
import org.ada.server.models.StorageType
import org.ada.server.models.datatrans.{InferDataSetTransformation, ResultDataSetSpec}
import org.incal.core.runnables.FutureRunnable
import org.incal.play.GuiceRunnableApp
import org.ada.server.services.ServiceTypes.DataSetCentralTransformer

class InferPPMIClinicalDataSet @Inject()(transformer: DataSetCentralTransformer) extends FutureRunnable {

  override def runAsFuture = {
    // spec
    val spec = InferDataSetTransformation(
      sourceDataSetId = "ppmi.raw_clinical_visit",
      resultDataSetSpec = ResultDataSetSpec(
        id = "ppmi.clinical_visit",
        name = "Clinical Visit",
        storageType = StorageType.ElasticSearch
      ),
      maxEnumValuesCount = Some(50),
      minAvgValuesPerEnum = Some(10),
      inferenceGroupSize = None,
      inferenceGroupsInParallel = None,
      streamSpec = StreamSpec(
        batchSize = Some(100)
      )
    )

    // transform
    transformer(spec)
  }
}

object InferPPMIClinicalDataSet extends GuiceRunnableApp[InferPPMIClinicalDataSet]