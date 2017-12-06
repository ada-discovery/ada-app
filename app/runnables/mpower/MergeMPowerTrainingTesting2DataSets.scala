package runnables.mpower

import javax.inject.Inject

import models.StorageType
import runnables.{FutureRunnable, GuiceBuilderRunnable, InputFutureRunnable}
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
    Seq(Some("accel_walking_outboundu002ejsonu002eitems"), Some("accel_walking_outboundu002ejsonu002eitems"), Some("accel_walking_outboundjsonitems")),
    Seq(Some("accel_walking_restu002ejsonu002eitems"), Some("accel_walking_restu002ejsonu002eitems"), Some("accel_walking_restjsonitems")),
    Seq(Some("accel_walking_returnu002ejsonu002eitems"), Some("accel_walking_returnu002ejsonu002eitems"), Some("accel_walking_returnjsonitems")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems"), Some("deviceMotion_walking_outboundu002ejsonu002eitems"), Some("deviceMotion_walking_outboundjsonitems")),
    Seq(Some("deviceMotion_walking_restu002ejsonu002eitems"), Some("deviceMotion_walking_restu002ejsonu002eitems"), Some("deviceMotion_walking_restjsonitems")),
    Seq(Some("deviceMotion_walking_returnu002ejsonu002eitems"), Some("deviceMotion_walking_returnu002ejsonu002eitems"), Some("deviceMotion_walking_returnjsonitems")),
    Seq(Some("pedometer_walking_outboundu002ejsonu002eitems"), Some("pedometer_walking_outboundu002ejsonu002eitems"), Some("pedometer_walking_outboundjsonitems")),
    Seq(Some("pedometer_walking_returnu002ejsonu002eitems"), Some("pedometer_walking_returnu002ejsonu002eitems"), Some("pedometer_walking_returnjsonitems")),
    Seq(Some("age"), None),
    Seq(Some("are-caretaker"), None),
    Seq(Some("deep-brain-stimulation"), None),
    Seq(Some("diagnosis-year"), None),
    Seq(Some("education"), None),
    Seq(Some("employment"), None),
    Seq(Some("gender"), None),
    Seq(Some("health-history"), None),
    Seq(Some("healthcare-provider"), None),
    Seq(Some("home-usage"), None),
    Seq(Some("last-smoked"), None),
    Seq(Some("maritalStatus"), None),
    Seq(Some("medTimepoint"), None),
    Seq(Some("medical-usage"), None),
    Seq(Some("medical-usage-yesterday"), None),
    Seq(Some("medication-start-year"), None),
    Seq(Some("onset-year"), None),
    Seq(Some("packs-per-day"), None),
    Seq(Some("past-participation"), None),
    Seq(Some("phone-usage"), None),
    Seq(Some("professional-diagnosis"), None),
    Seq(Some("race"), None),
    Seq(Some("smartphone"), None),
    Seq(Some("smoked"), None),
    Seq(Some("surgery"), None),
    Seq(Some("video-usage"), None),
    Seq(Some("years-smoking"), None)
  )

  override def runAsFuture(input: MergeMPowerTrainingTesting2DataSetsSpec) = {
    dataSetService.mergeDataSetsWoInference(
      mergedDataSetId,
      mergedDataSetName,
      StorageType.Mongo,
      Seq(dataSet1, dataSet2, dataSet3),
      fieldNameMappings,
      if (input.useDeltaInsert) Some("recordId") else None,
      input.processingBatchSize,
      input.saveBatchSize
    )
  }

  override def inputType = typeOf[MergeMPowerTrainingTesting2DataSetsSpec]
}

case class MergeMPowerTrainingTesting2DataSetsSpec(processingBatchSize: Option[Int], saveBatchSize: Option[Int], useDeltaInsert: Boolean)