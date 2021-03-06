package runnables.mpower

import javax.inject.Inject
import org.incal.core.dataaccess.StreamSpec
import org.ada.server.models.StorageType
import org.ada.server.models.datatrans.{LinkTwoDataSetsTransformation, ResultDataSetSpec}
import org.incal.core.runnables.FutureRunnable
import org.ada.server.services.ServiceTypes.DataSetCentralTransformer

class LinkMPowerTrainingAndDemographicsDataSets @Inject()(centralTransformer: DataSetCentralTransformer) extends FutureRunnable {

  private val walkingFieldNames =
    Seq(
      "ROW_ID",
      "ROW_VERSION",
      "recordId",
      "healthCode",
      "phoneInfo",
      "appVersion",
      "createdOn",
      "medTimepoint",
      "accel_walking_outboundu002ejsonu002eitems",
      "accel_walking_restu002ejsonu002eitems",
      "accel_walking_returnu002ejsonu002eitems",
      "deviceMotion_walking_outboundu002ejsonu002eitems",
      "deviceMotion_walking_restu002ejsonu002eitems",
      "deviceMotion_walking_returnu002ejsonu002eitems",
      "pedometer_walking_outboundu002ejsonu002eitems",
      "pedometer_walking_returnu002ejsonu002eitems"
    )

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

  private val dataSetLinkSpec = LinkTwoDataSetsTransformation(
    None,
    "mpower_challenge.walking_activity_training",
    "mpower_challenge.demographics_training",
    Seq(("healthCode", "healthCode")),
    walkingFieldNames,
    demographicsFieldNames,
    false,
    ResultDataSetSpec(
      "mpower_challenge.walking_activity_training_w_demographics",
      "Walking Activity Training with Demographics",
      StorageType.Mongo
    ),
    StreamSpec()
  )

  override def runAsFuture = centralTransformer(dataSetLinkSpec)
}