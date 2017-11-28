package runnables.mpower

import javax.inject.Inject

import models.StorageType
import runnables.{FutureRunnable, GuiceBuilderRunnable, InputFutureRunnable}
import services.DataSetService
import scala.reflect.runtime.universe.typeOf

class MergeMPowerTrainingTestingNormsDataSets @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[MergeMPowerTrainingTestingNormsDataSetsSpec] {

  private val dataSet1 = "mpower_challenge.walking_activity_training_norms_w_demographics"
  private val dataSet2 = "mpower_challenge.walking_activity_testing_norms"
  private val mergedDataSetId = "mpower_challenge.walking_activity_norms"
  private val mergedDataSetName = "Merged Norms"

  private val fieldNameMappings: Seq[Seq[Option[String]]] = Seq(
    Seq(Some("ROW_ID"), Some("ROW_ID")),
    Seq(Some("ROW_VERSION"), Some("ROW_VERSION")),
    Seq(Some("recordId"), Some("recordId")),
    Seq(Some("appVersion"), Some("appVersion")),
    Seq(Some("createdOn"), Some("createdOn")),
    Seq(Some("healthCode"), Some("healthCode")),
//    Seq(Some("phoneInfo"), Some("phoneInfo")),
    Seq(Some("accel_walking_outboundu002ejsonu002eitems_euclideanNorms"), Some("accel_walking_outboundjsonitems_euclideanNorms")),
    Seq(Some("accel_walking_outboundu002ejsonu002eitems_manhattanNorms"), Some("accel_walking_outboundjsonitems_manhattanNorms")),
    Seq(Some("accel_walking_restu002ejsonu002eitems_euclideanNorms"), Some("accel_walking_restjsonitems_euclideanNorms")),
    Seq(Some("accel_walking_restu002ejsonu002eitems_manhattanNorms"), Some("accel_walking_restjsonitems_manhattanNorms")),
    Seq(Some("accel_walking_returnu002ejsonu002eitems_euclideanNorms"), Some("accel_walking_returnjsonitems_euclideanNorms")),
    Seq(Some("accel_walking_returnu002ejsonu002eitems_manhattanNorms"), Some("accel_walking_returnjsonitems_manhattanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_attitude_euclideanNorms"), Some("deviceMotion_walking_outboundjsonitems_attitude_euclideanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_attitude_manhattanNorms"), Some("deviceMotion_walking_outboundjsonitems_attitude_manhattanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_gravity_euclideanNorms"), Some("deviceMotion_walking_outboundjsonitems_gravity_euclideanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_gravity_manhattanNorms"), Some("deviceMotion_walking_outboundjsonitems_gravity_manhattanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_rotationRate_euclideanNorms"), Some("deviceMotion_walking_outboundjsonitems_rotationRate_euclideanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_rotationRate_manhattanNorms"), Some("deviceMotion_walking_outboundjsonitems_rotationRate_manhattanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_userAcceleration_euclideanNorms"), Some("deviceMotion_walking_outboundjsonitems_userAcceleration_euclideanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_userAcceleration_manhattanNorms"), Some("deviceMotion_walking_outboundjsonitems_userAcceleration_manhattanNorms")),
    Seq(Some("deviceMotion_walking_restu002ejsonu002eitems_attitude_euclideanNorms"), Some("deviceMotion_walking_restjsonitems_attitude_euclideanNorms")),
    Seq(Some("deviceMotion_walking_restu002ejsonu002eitems_attitude_manhattanNorms"), Some("deviceMotion_walking_restjsonitems_attitude_manhattanNorms")),
    Seq(Some("deviceMotion_walking_restu002ejsonu002eitems_gravity_euclideanNorms"), Some("deviceMotion_walking_restjsonitems_gravity_euclideanNorms")),
    Seq(Some("deviceMotion_walking_restu002ejsonu002eitems_gravity_manhattanNorms"), Some("deviceMotion_walking_restjsonitems_gravity_manhattanNorms")),
    Seq(Some("deviceMotion_walking_restu002ejsonu002eitems_rotationRate_euclideanNorms"), Some("deviceMotion_walking_restjsonitems_rotationRate_euclideanNorms")),
    Seq(Some("deviceMotion_walking_restu002ejsonu002eitems_rotationRate_manhattanNorms"), Some("deviceMotion_walking_restjsonitems_rotationRate_manhattanNorms")),
    Seq(Some("deviceMotion_walking_restu002ejsonu002eitems_userAcceleration_euclideanNorms"), Some("deviceMotion_walking_restjsonitems_userAcceleration_euclideanNorms")),
    Seq(Some("deviceMotion_walking_restu002ejsonu002eitems_userAcceleration_manhattanNorms"), Some("deviceMotion_walking_restjsonitems_userAcceleration_manhattanNorms")),
    Seq(Some("deviceMotion_walking_returnu002ejsonu002eitems_attitude_euclideanNorms"), Some("deviceMotion_walking_returnjsonitems_attitude_euclideanNorms")),
    Seq(Some("deviceMotion_walking_returnu002ejsonu002eitems_attitude_manhattanNorms"), Some("deviceMotion_walking_returnjsonitems_attitude_manhattanNorms")),
    Seq(Some("deviceMotion_walking_returnu002ejsonu002eitems_gravity_euclideanNorms"), Some("deviceMotion_walking_returnjsonitems_gravity_euclideanNorms")),
    Seq(Some("deviceMotion_walking_returnu002ejsonu002eitems_gravity_manhattanNorms"), Some("deviceMotion_walking_returnjsonitems_gravity_manhattanNorms")),
    Seq(Some("deviceMotion_walking_returnu002ejsonu002eitems_rotationRate_euclideanNorms"), Some("deviceMotion_walking_returnjsonitems_rotationRate_euclideanNorms")),
    Seq(Some("deviceMotion_walking_returnu002ejsonu002eitems_rotationRate_manhattanNorms"), Some("deviceMotion_walking_returnjsonitems_rotationRate_manhattanNorms")),
    Seq(Some("deviceMotion_walking_returnu002ejsonu002eitems_userAcceleration_euclideanNorms"), Some("deviceMotion_walking_returnjsonitems_userAcceleration_euclideanNorms")),
    Seq(Some("deviceMotion_walking_returnu002ejsonu002eitems_userAcceleration_manhattanNorms"), Some("deviceMotion_walking_returnjsonitems_userAcceleration_manhattanNorms")),
    Seq(Some("pedometer_walking_outboundu002ejsonu002eitems"), Some("pedometer_walking_outboundjsonitems")),
    Seq(Some("pedometer_walking_returnu002ejsonu002eitems"), Some("pedometer_walking_returnjsonitems")),
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


  override def runAsFuture(input: MergeMPowerTrainingTestingNormsDataSetsSpec) = {
    dataSetService.mergeDataSetsWoInference(
      mergedDataSetId,
      mergedDataSetName,
      StorageType.Mongo,
      Seq(dataSet1, dataSet2),
      fieldNameMappings,
      if (input.useDeltaInsert) Some("recordId") else None,
      input.processingBatchSize,
      input.saveBatchSize
    )
  }

  override def inputType = typeOf[MergeMPowerTrainingTestingNormsDataSetsSpec]
}

case class MergeMPowerTrainingTestingNormsDataSetsSpec(processingBatchSize: Option[Int], saveBatchSize: Option[Int], useDeltaInsert: Boolean)