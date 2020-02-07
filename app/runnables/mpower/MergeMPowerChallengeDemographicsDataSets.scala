package runnables.mpower

import javax.inject.Inject
import org.ada.server.models.StorageType
import org.ada.server.models.datatrans.ResultDataSetSpec
import org.incal.core.runnables.FutureRunnable
import org.incal.play.GuiceRunnableApp
import org.ada.server.services.DataSetService

class MergeMPowerChallengeDemographicsDataSets @Inject()(
    dataSetService: DataSetService
  ) extends FutureRunnable {

  private val mergedDataSetId = "mpower_challenge.demographics"
  private val mergedDataSetName = "Demographics"
  private val demographicsDataSetId1 = "mpower_challenge.demographics_testing"
  private val demographicsDataSetId2 = "mpower_challenge.demographics_training"

  private val fieldNameMappings = Seq(
    Seq("ROW_ID", "ROW_ID"),
    Seq("ROW_VERSION", "ROW_VERSION"),
    Seq("age", "age"),
    Seq("appVersion", "appVersion"),
    Seq("createdOn", "createdOn"),
    Seq("education", "education"),
    Seq("employment", "employment"),
    Seq("gender", "gender"),
    Seq("healthhistory", "health-history"),
    Seq("healthCode", "healthCode"),
    Seq("homeusage", "home-usage"),
    Seq("lastsmoked", "last-smoked"),
    Seq("maritalStatus", "maritalStatus"),
    Seq("packsperday", "packs-per-day"),
    Seq("phoneusage", "phone-usage"),
    Seq("phoneInfo", "phoneInfo"),
    Seq("race", "race"),
    Seq("recordId", "recordId"),
    Seq("smartphone", "smartphone"),
    Seq("smoked", "smoked"),
    Seq("surgery", "surgery"),
    Seq("videousage", "video-usage"),
    Seq("yearssmoking", "years-smoking")
  )

  override def runAsFuture =
    dataSetService.mergeDataSets(
      ResultDataSetSpec(
        mergedDataSetId,
        mergedDataSetName,
        StorageType.ElasticSearch
      ),
      Seq(demographicsDataSetId1, demographicsDataSetId2),
      fieldNameMappings
    )
}

object MergeMPowerChallengeDemographicsDataSets extends GuiceRunnableApp[MergeMPowerChallengeDemographicsDataSets]