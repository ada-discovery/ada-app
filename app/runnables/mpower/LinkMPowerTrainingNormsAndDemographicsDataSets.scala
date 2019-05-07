package runnables.mpower

import javax.inject.Inject

import org.ada.server.models.StorageType
import org.ada.server.models.{DataSetLinkSpec, DerivedDataSetSpec}
import org.incal.core.runnables.FutureRunnable
import org.ada.server.services.DataSetService

class LinkMPowerTrainingNormsAndDemographicsDataSets @Inject()(dataSetService: DataSetService) extends FutureRunnable {

  private val walkingNormsFieldNames = Nil // take all

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

  private val dataSetLinkSpec = DataSetLinkSpec(
    "mpower_challenge.walking_activity_training_norms",
    "mpower_challenge.demographics_training",
    Seq("healthCode"),
    Seq("healthCode"),
    walkingNormsFieldNames,
    demographicsFieldNames,
    false,
    DerivedDataSetSpec(
      "mpower_challenge.walking_activity_training_norms_w_demographics",
      "Walking Activity Training Norms with Demographics",
      StorageType.Mongo
    ),
    Some(4),
    Some(1)
  )

  override def runAsFuture = dataSetService.linkDataSets(dataSetLinkSpec)
}
