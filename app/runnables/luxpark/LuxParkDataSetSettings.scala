package runnables.luxpark

import models.{StorageType, DataSetSetting}
import runnables.DataSetId._

object LuxParkDataSetSettings{

  val Clinical = DataSetSetting(
    None,
    lux_park_clinical,
    "cdisc_dm_usubjd",
    Some("_id"),
    Some("digitsf_score"),
    Some("bentons_score"),
    "digitsf_score" ,
    Some("consent_q0b"),
    None,
    Some("redcap_event_name"),
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val IBBLBiosamples = DataSetSetting(
    None,
    lux_park_ibbl_biosamples,
    "sampleid",
    Some("_id"),
    Some("kittypeversion"),
    Some("qtycurrent"),
    "sampletypeid",
    Some("kitcreationdate"),
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val IBBLBiosampleTests = DataSetSetting(
    None,
    lux_park_ibbl_biosample_tests,
    "sampleid",
    Some("_id"),
    Some("dataset"),
    Some("paramlistversionid"),
    "testapproval",
    None,
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val MPowerMyThoughts = DataSetSetting(
    None,
    lux_park_mpower_my_thoughts,
    "externalId",
    Some("_id"),
    Some("feeling_better"),
    Some("feeling_worse"),
    "feeling_better",
    Some("createdOn"),
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val MPowerDemographics = DataSetSetting(
    None,
    lux_park_mpower_demographics,
    "externalId",
    Some("_id"),
    Some("NonIdentifiableDemographicsu002ejsonu002epatientHeightInches"),
    Some("NonIdentifiableDemographicsu002ejsonu002epatientWeightPounds"),
    "NonIdentifiableDemographicsu002ejsonu002epatientWakeUpTime" ,
    Some("createdOn"),
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val MPowerEnrollmentSurvey = DataSetSetting(
    None,
    lux_park_mpower_enrollment_survey,
    "externalId",
    Some("_id"),
    Some("age"),
    Some("years-smoking"),
    "education" ,
    Some("createdOn"),
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val MPowerPDEnrollmentSurvey = DataSetSetting(
    None,
    lux_park_mpower_pd_enrollment_survey,
    "externalId",
    Some("_id"),
    Some("ROW_ID"),
    Some("ROW_ID"),
    "medication-start-year" ,
    Some("createdOn"),
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val MPowerTremorActivity = DataSetSetting(
    None,
    lux_park_mpower_tremor_activity,
    "externalId",
    Some("_id"),
    Some("ROW_ID"),
    Some("ROW_ID"),
    "phoneInfo",
    Some("createdOn"),
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val MPowerWalkingActivity = DataSetSetting(
    None,
    lux_park_mpower_walking_activity,
    "externalId",
    Some("_id"),
    Some("ROW_ID"),
    Some("ROW_ID"),
    "momentInDayFormatu002ejsonu002echoiceAnswers" ,
    Some("createdOn"),
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val MPowerTappingActivity = DataSetSetting(
    None,
    lux_park_mpower_tapping_activity,
    "externalId",
    Some("_id"),
    Some("ROW_ID"),
    Some("ROW_ID"),
    "momentInDayFormatu002ejsonu002echoiceAnswers" ,
    Some("createdOn"),
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val MPowerVoiceActivity = DataSetSetting(
    None,
    lux_park_mpower_voice_activity,
    "externalId",
    Some("_id"),
    Some("ROW_ID"),
    Some("ROW_ID"),
    "appVersion" ,
    Some("createdOn"),
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )

  val MPowerMemoryActivity = DataSetSetting(
    None,
    lux_park_mpower_memory_activity,
    "externalId",
    Some("_id"),
    Some("ROW_ID"),
    Some("ROW_ID"),
    "appVersion" ,
    Some("createdOn"),
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )
}