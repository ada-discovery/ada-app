package runnables.mpower

import javax.inject.Inject

import models.StorageType
import models.ml.ResultDataSetSpec
import runnables.{FutureRunnable, GuiceBuilderRunnable, InputFutureRunnable}
import services.DataSetService

import scala.reflect.runtime.universe.typeOf

class MergeMPowerTrainingTestingNormsDiffDataSets @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[MergeMPowerTrainingTestingNormsDiffDataSetsSpec] {

  private val dataSet1 = "mpower_challenge.walking_activity_training_norms_outbound_diff"
  private val dataSet2 = "mpower_challenge.walking_activity_testing_norms_outbound_diff"
  private val mergedDataSetId = "mpower_challenge.walking_activity_norms_outbound_diff"
  private val mergedDataSetName = "Merged Norms Outbound Diff"

  private val fieldNameMappings: Seq[Seq[Option[String]]] = Seq(
    Seq(Some("ROW_ID"), Some("ROW_ID")),
    Seq(Some("ROW_VERSION"), Some("ROW_VERSION")),
    Seq(Some("recordId"), Some("recordId")),
    Seq(Some("appVersion"), Some("appVersion")),
    Seq(Some("createdOn"), Some("createdOn")),
    Seq(Some("healthCode"), Some("healthCode")),
//    Seq(Some("phoneInfo"), Some("phoneInfo")),
    Seq(Some("accel_walking_outboundu002ejsonu002eitems_euclideanNorms_Diff"), Some("accel_walking_outboundjsonitems_euclideanNorms_Diff")),
    Seq(Some("accel_walking_outboundu002ejsonu002eitems_manhattanNorms_Diff"), Some("accel_walking_outboundjsonitems_manhattanNorms_Diff")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_attitude_euclideanNorms_Diff"), Some("deviceMotion_walking_outboundjsonitems_attitude_euclideanNorms_Diff")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_attitude_manhattanNorms_Diff"), Some("deviceMotion_walking_outboundjsonitems_attitude_manhattanNorms_Diff")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_gravity_euclideanNorms_Diff"), Some("deviceMotion_walking_outboundjsonitems_gravity_euclideanNorms_Diff")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_gravity_manhattanNorms_Diff"), Some("deviceMotion_walking_outboundjsonitems_gravity_manhattanNorms_Diff")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_rotationRate_euclideanNorms_Diff"), Some("deviceMotion_walking_outboundjsonitems_rotationRate_euclideanNorms_Diff")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_rotationRate_manhattanNorms_Diff"), Some("deviceMotion_walking_outboundjsonitems_rotationRate_manhattanNorms_Diff")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_userAcceleration_euclideanNorms_Diff"), Some("deviceMotion_walking_outboundjsonitems_userAcceleration_euclideanNorms_Diff")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_userAcceleration_manhattanNorms_Diff"), Some("deviceMotion_walking_outboundjsonitems_userAcceleration_manhattanNorms_Diff")),
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

  override def runAsFuture(input: MergeMPowerTrainingTestingNormsDiffDataSetsSpec) = {
    dataSetService.mergeDataSetsWoInference(
      ResultDataSetSpec(
        mergedDataSetId,
        mergedDataSetName,
        StorageType.Mongo
      ),
      Seq(dataSet1, dataSet2),
      fieldNameMappings,
      if (input.useDeltaInsert) Some("recordId") else None,
      input.processingBatchSize,
      input.saveBatchSize
    )
  }

  override def inputType = typeOf[MergeMPowerTrainingTestingNormsDiffDataSetsSpec]
}

case class MergeMPowerTrainingTestingNormsDiffDataSetsSpec(processingBatchSize: Option[Int], saveBatchSize: Option[Int], useDeltaInsert: Boolean)