package runnables.ppmi

import javax.inject.Inject

import field.{FieldTypeHelper, FieldTypeInferrerFactory}
import org.incal.core.FutureRunnable
import org.incal.play.GuiceRunnableApp
import services.DataSetService

class InferPPMIClinicalDataSet @Inject()(dataSetService: DataSetService) extends FutureRunnable {

  override def runAsFuture = {
    val fieldTypeInferrerFactory = FieldTypeInferrerFactory(
      FieldTypeHelper.fieldTypeFactory(),
      50,
      10,
      FieldTypeHelper.arrayDelimiter
    )

    dataSetService.translateDataAndDictionaryOptimal(
      "ppmi.raw_clinical_visit",
      "ppmi.clinical_visit",
      "Clinical Visit",
      None,
      None,
      Some(100),
      None,
      None,
      Some(fieldTypeInferrerFactory.applyJson)
    )
  }
}

object InferPPMIClinicalDataSet extends GuiceRunnableApp[InferPPMIClinicalDataSet]