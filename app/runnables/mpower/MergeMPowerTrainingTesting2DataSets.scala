package runnables.mpower

import javax.inject.Inject

import org.ada.server.dataaccess.StreamSpec
import org.ada.server.models.StorageType
import models.ml.DerivedDataSetSpec
import org.incal.core.InputFutureRunnable
import services.DataSetService

import scala.reflect.runtime.universe.typeOf

class MergeMPowerTrainingTesting2DataSets @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[MergeMPowerTrainingTesting2DataSetsSpec] {

  private val dataSet1 = "mpower_challenge.walking_activity_training_2"
  private val dataSet2 = "mpower_challenge.walking_activity_supplement_training"
  private val dataSet3 = "mpower_challenge.walking_activity_testing_2"
  private val mergedDataSetId = "mpower_challenge.walking_activity_2"
  private val mergedDataSetName = "Merged Activity"

  private val fieldNameMappings: Seq[Seq[Option[String]]] = Seq(
    Seq(Some("ROW_ID"), Some("ROW_ID"), Some("ROW_ID")),
    Seq(Some("ROW_VERSION"), Some("ROW_VERSION"), Some("ROW_VERSION")),
    Seq(Some("recordId"), Some("recordId"), Some("recordId")),
    Seq(Some("appVersion"), Some("appVersion"), Some("appVersion")),
    Seq(Some("createdOn"), Some("createdOn"), Some("createdOn")),
    Seq(Some("healthCode"), Some("healthCode"), Some("healthCode")),
    Seq(Some("phoneInfo"), Some("phoneInfo"), Some("phoneInfo")),
    Seq(Some("medTimepoint"), Some("momentInDayFormatu002ejsonu002echoiceAnswers"), None),
    Seq(Some("accel_walking_outboundu002ejsonu002eitems"), Some("accel_walking_outboundu002ejsonu002eitems"), Some("accel_walking_outboundu002ejsonu002eitems")),
    Seq(Some("accel_walking_restu002ejsonu002eitems"), Some("accel_walking_restu002ejsonu002eitems"), Some("accel_walking_restu002ejsonu002eitems")),
    Seq(Some("accel_walking_returnu002ejsonu002eitems"), Some("accel_walking_returnu002ejsonu002eitems"), Some("accel_walking_returnu002ejsonu002eitems")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems"), Some("deviceMotion_walking_outboundu002ejsonu002eitems"), Some("deviceMotion_walking_outboundu002ejsonu002eitems")),
    Seq(Some("deviceMotion_walking_restu002ejsonu002eitems"), Some("deviceMotion_walking_restu002ejsonu002eitems"), Some("deviceMotion_walking_restu002ejsonu002eitems")),
    Seq(Some("deviceMotion_walking_returnu002ejsonu002eitems"), Some("deviceMotion_walking_returnu002ejsonu002eitems"), Some("deviceMotion_walking_returnu002ejsonu002eitems")),
    Seq(Some("pedometer_walking_outboundu002ejsonu002eitems"), Some("pedometer_walking_outboundu002ejsonu002eitems"), Some("pedometer_walking_outboundu002ejsonu002eitems")),
    Seq(Some("pedometer_walking_returnu002ejsonu002eitems"), Some("pedometer_walking_returnu002ejsonu002eitems"), Some("pedometer_walking_returnu002ejsonu002eitemss"))
  )

  override def runAsFuture(input: MergeMPowerTrainingTesting2DataSetsSpec) =
    dataSetService.mergeDataSetsWoInference(
      Seq(dataSet1, dataSet2, dataSet3),
      fieldNameMappings,
      true,
      DerivedDataSetSpec(
        mergedDataSetId,
        mergedDataSetName,
        StorageType.Mongo
      ),
      StreamSpec(batchSize = input.batchSize)
    )

  override def inputType = typeOf[MergeMPowerTrainingTesting2DataSetsSpec]
}

case class MergeMPowerTrainingTesting2DataSetsSpec(
  batchSize: Option[Int]
)