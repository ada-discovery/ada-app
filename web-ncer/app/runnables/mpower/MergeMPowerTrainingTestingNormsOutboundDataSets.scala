package runnables.mpower

import javax.inject.Inject
import org.incal.core.dataaccess.StreamSpec
import org.ada.server.models.StorageType
import org.ada.server.models.datatrans.{MergeMultiDataSetsTransformation, ResultDataSetSpec}
import org.incal.core.runnables.InputFutureRunnableExt
import org.ada.server.services.ServiceTypes.DataSetCentralTransformer

class MergeMPowerTrainingTestingNormsOutboundDataSets @Inject()(centralTransformer: DataSetCentralTransformer) extends InputFutureRunnableExt[MergeMPowerTrainingTestingNormsOutboundDataSetsSpec] {

  private val dataSet1 = "mpower_challenge.walking_activity_training_norms_w_demographics_outbound"
  private val dataSet2 = "mpower_challenge.walking_activity_testing_norms_outbound"
  private val mergedDataSetId = "mpower_challenge.walking_activity_norms_outbound"
  private val mergedDataSetName = "Merged Norms Outbound"

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
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_attitude_euclideanNorms"), Some("deviceMotion_walking_outboundjsonitems_attitude_euclideanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_attitude_manhattanNorms"), Some("deviceMotion_walking_outboundjsonitems_attitude_manhattanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_gravity_euclideanNorms"), Some("deviceMotion_walking_outboundjsonitems_gravity_euclideanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_gravity_manhattanNorms"), Some("deviceMotion_walking_outboundjsonitems_gravity_manhattanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_rotationRate_euclideanNorms"), Some("deviceMotion_walking_outboundjsonitems_rotationRate_euclideanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_rotationRate_manhattanNorms"), Some("deviceMotion_walking_outboundjsonitems_rotationRate_manhattanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_userAcceleration_euclideanNorms"), Some("deviceMotion_walking_outboundjsonitems_userAcceleration_euclideanNorms")),
    Seq(Some("deviceMotion_walking_outboundu002ejsonu002eitems_userAcceleration_manhattanNorms"), Some("deviceMotion_walking_outboundjsonitems_userAcceleration_manhattanNorms")),
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

  override def runAsFuture(input: MergeMPowerTrainingTestingNormsOutboundDataSetsSpec) = {
    val spec = MergeMultiDataSetsTransformation(
      None,
      Seq(dataSet1, dataSet2),
      fieldNameMappings,
      true,
      ResultDataSetSpec(
        mergedDataSetId,
        mergedDataSetName,
        StorageType.Mongo
      ),
      StreamSpec(batchSize = input.batchSize)
    )

    centralTransformer(spec)
  }
}

case class MergeMPowerTrainingTestingNormsOutboundDataSetsSpec(batchSize: Option[Int])