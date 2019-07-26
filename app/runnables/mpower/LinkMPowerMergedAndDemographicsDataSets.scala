package runnables.mpower

import javax.inject.Inject
import org.ada.server.dataaccess.StreamSpec
import org.ada.server.models.StorageType
import org.ada.server.models.datatrans.{LinkTwoDataSetsTransformation, ResultDataSetSpec}
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.ada.server.services.DataSetService
import org.ada.server.services.ServiceTypes.DataSetCentralTransformer

class LinkMPowerMergedAndDemographicsDataSets @Inject()(centralTransformer: DataSetCentralTransformer) extends InputFutureRunnableExt[LinkMPowerMergedAndDemographicsDataSetsSpec] {

  private val walkingFieldNames = Nil // take all

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

  private def dataSetLinkSpec(
    input: LinkMPowerMergedAndDemographicsDataSetsSpec
  ) = LinkTwoDataSetsTransformation(
    None,
    "mpower_challenge.walking_activity_2",
    "mpower_challenge.demographics_training_2",
    Seq(("healthCode", "healthCode")),
    walkingFieldNames,
    demographicsFieldNames,
    false,
    ResultDataSetSpec(
      "mpower_challenge.walking_activity_2_w_demographics",
      "Merged Activity with Demographics",
      StorageType.Mongo
    ),
    input.streamSpec
  )

  override def runAsFuture(
    input: LinkMPowerMergedAndDemographicsDataSetsSpec
  ) =
    centralTransformer(dataSetLinkSpec(input))
}

case class LinkMPowerMergedAndDemographicsDataSetsSpec(streamSpec: StreamSpec)