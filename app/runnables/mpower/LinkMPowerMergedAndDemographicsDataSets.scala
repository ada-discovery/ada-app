package runnables.mpower

import javax.inject.Inject

import org.ada.server.models.StorageType
import org.ada.server.models.{DataSetLinkSpec, DerivedDataSetSpec}
import org.incal.core.runnables.InputFutureRunnable
import org.ada.server.services.DataSetService

import scala.reflect.runtime.universe.typeOf

class LinkMPowerMergedAndDemographicsDataSets @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[LinkMPowerMergedAndDemographicsDataSetsSpec] {

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

  private def dataSetLinkSpec(input: LinkMPowerMergedAndDemographicsDataSetsSpec) = DataSetLinkSpec(
    "mpower_challenge.walking_activity_2",
    "mpower_challenge.demographics_training_2",
    Seq("healthCode"),
    Seq("healthCode"),
    walkingFieldNames,
    demographicsFieldNames,
    false,
    DerivedDataSetSpec(
      "mpower_challenge.walking_activity_2_w_demographics",
      "Merged Activity with Demographics",
      StorageType.Mongo
    ),
    input.processingBatchSize,
    input.saveBatchSize
  )

  override def runAsFuture(input: LinkMPowerMergedAndDemographicsDataSetsSpec) =
    dataSetService.linkDataSets(dataSetLinkSpec(input))

  override def inputType = typeOf[LinkMPowerMergedAndDemographicsDataSetsSpec]
}

case class LinkMPowerMergedAndDemographicsDataSetsSpec(processingBatchSize: Option[Int], saveBatchSize: Option[Int])