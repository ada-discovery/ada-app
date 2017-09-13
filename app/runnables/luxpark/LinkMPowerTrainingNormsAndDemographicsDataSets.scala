package runnables.luxpark

import javax.inject.Inject

import models.StorageType
import models.ml.{DataSetLink, DataSetTransformationCore}
import runnables.FutureRunnable
import services.DataSetService

class LinkMPowerTrainingNormsAndDemographicsDataSets @Inject()(dataSetService: DataSetService) extends FutureRunnable {

  private val walkingNormsFieldNames = Nil // take all
//    Seq(
//      "ROW_ID",
//      "ROW_VERSION",
//      "recordId",
//      "healthCode",
//      "phoneInfo",
//      "appVersion",
//      "createdOn",
//      "medTimepoint",
//      "accel_walking_outboundu002ejsonu002eitems_euclideanNorms",
//      "accel_walking_outboundu002ejsonu002eitems_manhattanNorms",
//      "accel_walking_restu002ejsonu002eitems_euclideanNorms",
//      "accel_walking_restu002ejsonu002eitems_manhattanNorms",
//      "accel_walking_returnu002ejsonu002eitems_euclideanNorms",
//      "accel_walking_returnu002ejsonu002eitems_manhattanNorms",
//      "deviceMotion_walking_outboundu002ejsonu002eitems_attitude_euclideanNorms",
//      "deviceMotion_walking_outboundu002ejsonu002eitems_attitude_manhattanNorms",
//      "deviceMotion_walking_outboundu002ejsonu002eitems_gravity_euclideanNorms",
//      "deviceMotion_walking_outboundu002ejsonu002eitems_gravity_manhattanNorms",
//      "deviceMotion_walking_outboundu002ejsonu002eitems_rotationRate_euclideanNorms",
//      "deviceMotion_walking_outboundu002ejsonu002eitems_rotationRate_manhattanNorms",
//      "deviceMotion_walking_outboundu002ejsonu002eitems_userAcceleration_euclideanNorms",
//      "deviceMotion_walking_outboundu002ejsonu002eitems_userAcceleration_manhattanNorms",
//      "deviceMotion_walking_restu002ejsonu002eitems_attitude_euclideanNorms",
//      "deviceMotion_walking_restu002ejsonu002eitems_attitude_manhattanNorms",
//      "deviceMotion_walking_restu002ejsonu002eitems_gravity_euclideanNorms",
//      "deviceMotion_walking_restu002ejsonu002eitems_gravity_manhattanNorms",
//      "deviceMotion_walking_restu002ejsonu002eitems_rotationRate_euclideanNorms",
//      "deviceMotion_walking_restu002ejsonu002eitems_rotationRate_manhattanNorms",
//      "deviceMotion_walking_restu002ejsonu002eitems_userAcceleration_euclideanNorms",
//      "deviceMotion_walking_restu002ejsonu002eitems_userAcceleration_manhattanNorms",
//      "deviceMotion_walking_returnu002ejsonu002eitems_attitude_euclideanNorms",
//      "deviceMotion_walking_returnu002ejsonu002eitems_attitude_manhattanNorms",
//      "deviceMotion_walking_returnu002ejsonu002eitems_gravity_euclideanNorms",
//      "deviceMotion_walking_returnu002ejsonu002eitems_gravity_manhattanNorms",
//      "deviceMotion_walking_returnu002ejsonu002eitems_rotationRate_euclideanNorms",
//      "deviceMotion_walking_returnu002ejsonu002eitems_rotationRate_manhattanNorms",
//      "deviceMotion_walking_returnu002ejsonu002eitems_userAcceleration_euclideanNorms",
//      "deviceMotion_walking_returnu002ejsonu002eitems_userAcceleration_manhattanNorms",
//      "pedometer_walking_outboundu002ejsonu002eitems",
//      "pedometer_walking_returnu002ejsonu002eitems"
//    )

  private val demographicsFieldNames =
    Seq(
//      "ROW_ID",
//      "ROW_VERSION",
//      "createdOn",
//      "recordId",
//      "healthCode",
//      "phoneInfo",
//      "appVersion",
      "age",
      "are-caretaker",
      "deep-brain-stimulation",
      "diagnosis-year",
      "education",
      "employment",
      "gender",
      "health-history",
      "healthcare-provider",
      "home-usage",
      "last-smoked",
      "maritalStatus",
      "medical-usage",
      "medical-usage-yesterday",
      "medication-start-year",
      "onset-year",
      "packs-per-day",
      "past-participation",
      "phone-usage",
      "professional-diagnosis",
      "race",
      "smartphone",
      "smoked",
      "surgery",
      "video-usage",
      "years-smoking"
    )

  private val dataSetLinkSpec = DataSetLink(
    "mpower_challenge.walking_activity_training_norms",
    "mpower_challenge.demographics_training",
    Seq(
      ("healthCode", "healthCode")
    ),
    walkingNormsFieldNames,
    demographicsFieldNames,
    DataSetTransformationCore(
      "mpower_challenge.walking_activity_training_norms_w_demographics",
      "Walking Activity Training Norms with Demographics",
      StorageType.Mongo,
      Some(4),
      Some(1)
    )
  )

  override def runAsFuture = dataSetService.linkDataSets(dataSetLinkSpec)
}
