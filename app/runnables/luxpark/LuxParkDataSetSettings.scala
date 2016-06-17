package runnables.luxpark

import runnables.DataSetId._
import models.DataSetSetting

object LuxParkDataSetSettings {

  val Clinical = DataSetSetting(
    None,
    lux_park_clinical,
    "cdisc_dm_usubjd",
    "cdisc_dm_usubjd",
    Seq("cdisc_dm_usubjd", "redcap_event_name", "cdisc_dm_subjid_2", "dm_site", "cdisc_dm_brthdtc", "cdisc_dm_sex", "cdisc_sc_sctestcd_maritstat"),
    Seq("cdisc_dm_sex", "control_q1", "cdisc_sc_sctestcd_maritstat", "sv_age"),
    "digitsf_score",
    "bentons_score",
    "digitsf_score" ,
    Some("redcap_event_name"),
    Map(("\r", " "), ("\n", " "))
  )

  val IBBLBiosamples = DataSetSetting(
    None,
    lux_park_ibbl_biosamples,
    "SAMPLEID",
    "SAMPLEID",
    Seq("SAMPLEID", "SAMPLETYPEID", "KITTYPE", "STORAGESTATUS", "QTYCURRENT"),
    Seq("SAMPLETYPEID", "KITTYPE", "STORAGESTATUS", "QTYCURRENT"),
    "KITTYPEVERSION",
    "QTYCURRENT",
    "SAMPLETYPEID" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val IBBLBiosampleTests = DataSetSetting(
    None,
    lux_park_ibbl_biosample_tests,
    "SAMPLEID",
    "SAMPLEID",
    Seq("SAMPLEID", "SSTUDYID", "TESTAPPROVAL", "PARAMDESC", "TESTDESC"),
    Seq("TESTAPPROVAL", "TESTSTATUS", "PARAMDESC", "PARAMLISTDESC"),
    "DATASET",
    "PARAMLISTVERSIONID",
    "TESTAPPROVAL" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val MPowerMyThoughts = DataSetSetting(
    None,
    lux_park_mpower_my_thoughts,
    "externalId",
    "externalId",
    Seq("dataGroups", "externalId", "createdOn", "appVersion"),
    Seq("dataGroups", "externalId", "createdOn", "appVersion"),
    "feeling_better",
    "feeling_worse",
    "feeling_better" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val MPowerDemographics = DataSetSetting(
    None,
    lux_park_mpower_demographics,
    "externalId",
    "externalId",
    Seq("dataGroups", "externalId", "NonIdentifiableDemographicsu002ejsonu002epatientHeightInches", "NonIdentifiableDemographicsu002ejsonu002epatientWeightPounds"),
    Seq("dataGroups", "externalId", "NonIdentifiableDemographicsu002ejsonu002epatientHeightInches", "NonIdentifiableDemographicsu002ejsonu002epatientWeightPounds"),
    "NonIdentifiableDemographicsu002ejsonu002epatientHeightInches",
    "NonIdentifiableDemographicsu002ejsonu002epatientWeightPounds",
    "NonIdentifiableDemographicsu002ejsonu002epatientWakeUpTime" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val MPowerEnrollmentSurvey = DataSetSetting(
    None,
    lux_park_mpower_enrollment_survey,
    "externalId",
    "externalId",
    Seq("externalId", "dataGroups", "createdOn", "appVersion", "phoneInfo", "age", "gender", "race", "education", "employment", "years-smoking"),
    Seq("dataGroups", "age", "gender", "education"),
    "age",
    "years-smoking",
    "education" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val MPowerPDEnrollmentSurvey = DataSetSetting(
    None,
    lux_park_mpower_pd_enrollment_survey,
    "externalId",
    "externalId",
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "diagnosis-year", "onset-year", "deep-brain-stimulation"),
    Seq("dataGroups", "externalId", "appVersion", "medication-bool"),
    "ROW_ID",
    "ROW_ID",
    "medication-start-year" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val MPowerTremorActivity = DataSetSetting(
    None,
    lux_park_mpower_tremor_activity,
    "externalId",
    "externalId",
    Seq("dataGroups", "externalId", "createdOn", "momentInDayFormatu002ejsonu002echoiceAnswers", "appVersion"),
    Seq("dataGroups", "externalId", "appVersion", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    "ROW_ID",
    "ROW_ID",
    "phoneInfo" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val MPowerWalkingActivity = DataSetSetting(
    None,
    lux_park_mpower_walking_activity,
    "externalId",
    "externalId",
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "phoneInfo"),
    Seq("dataGroups", "externalId", "phoneInfo", "appVersion"),
    "ROW_ID",
    "ROW_ID",
    "momentInDayFormatu002ejsonu002echoiceAnswers" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val MPowerTappingActivity = DataSetSetting(
    None,
    lux_park_mpower_tapping_activity,
    "externalId",
    "externalId",
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "phoneInfo", "momentInDayFormatu002ejsonu002echoiceAnswers", "tapping_leftu002ejsonu002eTappingSamples", "tapping_rightu002ejsonu002eTappingSamples", "accel_tapping_rightu002ejsonu002eitems", "accel_tapping_leftu002ejsonu002eitems"),
    Seq("dataGroups", "externalId", "phoneInfo", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    "ROW_ID",
    "ROW_ID",
    "momentInDayFormatu002ejsonu002echoiceAnswers" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val MPowerVoiceActivity = DataSetSetting(
    None,
    lux_park_mpower_voice_activity,
    "externalId",
    "externalId",
    Seq("dataGroups", "externalId", "createdOn", "momentInDayFormatu002ejsonu002echoiceAnswers", "appVersion", "audio_audiou002em4a", "audio_countdownu002em4a"),
    Seq("dataGroups", "externalId", "appVersion", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    "ROW_ID",
    "ROW_ID",
    "appVersion" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )

  val MPowerMemoryActivity = DataSetSetting(
    None,
    lux_park_mpower_memory_activity,
    "externalId",
    "externalId",
    Seq("dataGroups", "externalId", "createdOn", "momentInDayFormatu002ejsonu002echoiceAnswers", "appVersion"),
    Seq("dataGroups", "externalId", "appVersion", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    "ROW_ID",
    "ROW_ID",
    "appVersion" ,
    None,
    Map(("\r", " "), ("\n", " "))
  )
}