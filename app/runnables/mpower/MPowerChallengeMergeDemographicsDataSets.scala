package runnables.mpower

import javax.inject.Inject

import runnables.GuiceBuilderRunnable
import services.DataSetService

import scala.concurrent.Await
import scala.concurrent.duration._

class MPowerChallengeMergeDemographicsDataSets @Inject() (
    dataSetService: DataSetService
  ) extends Runnable {

  private val timeout = 120000 millis

  val mergedDataSetId = "mpower_challenge.demographics"
  val mergedDataSetName = "Demographics"
  val demographicsDataSetId1 = "mpower_challenge.demographics_testing"
  val demographicsDataSetId2 = "mpower_challenge.demographics_training"

  val fieldNameMappings = Seq(
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

  override def run() = {
    val future = dataSetService.mergeDataSets(
      mergedDataSetId,
      mergedDataSetName,
      None,
      Seq(demographicsDataSetId1, demographicsDataSetId2),
      fieldNameMappings
    )
    Await.result(future, timeout)
  }
}

object MPowerChallengeMergeDemographicsDataSets extends GuiceBuilderRunnable[MPowerChallengeMergeDemographicsDataSets] with App { run }
