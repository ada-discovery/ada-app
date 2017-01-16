package runnables.luxpark

import models.{StorageType, DataSetSetting}
import runnables.DataSetId._

object LuxParkDataSetSettings{

  val Clinical = DataSetSetting(
    None,
    lux_park_clinical,
    "cdisc_dm_usubjd",
    Some("_id"),
    "digitsf_score",
    "bentons_score",
    "digitsf_score" ,
    "consent_q0b",
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
    "kittypeversion",
    "qtycurrent",
    "sampletypeid",
    "kitcreationdate",
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
    "dataset",
    "paramlistversionid",
    "testapproval",
    "",
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
    "feeling_better",
    "feeling_worse",
    "feeling_better",
    "createdOn",
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
    "NonIdentifiableDemographicsu002ejsonu002epatientHeightInches",
    "NonIdentifiableDemographicsu002ejsonu002epatientWeightPounds",
    "NonIdentifiableDemographicsu002ejsonu002epatientWakeUpTime" ,
    "createdOn",
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
    "age",
    "years-smoking",
    "education" ,
    "createdOn",
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
    "ROW_ID",
    "ROW_ID",
    "medication-start-year" ,
    "createdOn",
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
    "ROW_ID",
    "ROW_ID",
    "phoneInfo" ,
    "createdOn",
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
    "ROW_ID",
    "ROW_ID",
    "momentInDayFormatu002ejsonu002echoiceAnswers" ,
    "createdOn",
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
    "ROW_ID",
    "ROW_ID",
    "momentInDayFormatu002ejsonu002echoiceAnswers" ,
    "createdOn",
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
    "ROW_ID",
    "ROW_ID",
    "appVersion" ,
    "createdOn",
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
    "ROW_ID",
    "ROW_ID",
    "appVersion" ,
    "createdOn",
    None,
    None,
    Map(("\r", " "), ("\n", " ")),
    StorageType.Mongo,
    false
  )
}