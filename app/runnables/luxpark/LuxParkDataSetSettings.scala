package runnables.luxpark

import dataaccess.DataSetSetting
import runnables.DataSetId._

object LuxParkDataSetSettings{

  val Clinical = DataSetSetting.apply2(
    None,
    lux_park_clinical,
    "cdisc_dm_usubjd",
    "_id",
    Seq("cdisc_dm_usubjd", "redcap_event_name", "cdisc_dm_subjid_2", "dm_site", "cdisc_dm_brthdtc", "cdisc_dm_sex", "cdisc_sc_sctestcd_maritstat"),
    Seq("cdisc_dm_sex", "control_q1", "cdisc_sc_sctestcd_maritstat", "sv_age"),
    3,
    "digitsf_score",
    "bentons_score",
    "digitsf_score" ,
    Some("redcap_event_name"),
    Map(("\r", " "), ("\n", " ")),
    true
  )

  val IBBLBiosamples = DataSetSetting.apply2(
    None,
    lux_park_ibbl_biosamples,
    "sampleid",
    "_id",
    Seq("sampleid", "sampletypeid", "kittype", "storagestatus", "qtycurrent"),
    Seq("sampletypeid", "kittype", "storagestatus", "qtycurrent"),
    3,
    "kittypeversion",
    "qtycurrent",
    "sampletypeid" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    true
  )

  val IBBLBiosampleTests = DataSetSetting.apply2(
    None,
    lux_park_ibbl_biosample_tests,
    "sampleid",
    "_id",
    Seq("sampleid", "sstudyid", "testapproval", "paramdesc", "testdesc", "createdt"),
    Seq("paramdesc", "testapproval", "teststatus", "paramlistdesc", "datatypes", "paramid"),
    3,
    "dataset",
    "paramlistversionid",
    "testapproval" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    true
  )

  val MPowerMyThoughts = DataSetSetting.apply2(
    None,
    lux_park_mpower_my_thoughts,
    "externalId",
    "_id",
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "feeling_better", "feeling_worse"),
    Seq("dataGroups", "externalId", "appVersion", "feeling_worse"),
    3,
    "feeling_better",
    "feeling_worse",
    "feeling_better" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    true
  )

  val MPowerDemographics = DataSetSetting.apply2(
    None,
    lux_park_mpower_demographics,
    "externalId",
    "_id",
    Seq("dataGroups", "externalId", "NonIdentifiableDemographicsu002ejsonu002epatientHeightInches", "NonIdentifiableDemographicsu002ejsonu002epatientWeightPounds"),
    Seq("dataGroups", "externalId", "NonIdentifiableDemographicsu002ejsonu002epatientHeightInches", "NonIdentifiableDemographicsu002ejsonu002epatientWeightPounds"),
    3,
    "NonIdentifiableDemographicsu002ejsonu002epatientHeightInches",
    "NonIdentifiableDemographicsu002ejsonu002epatientWeightPounds",
    "NonIdentifiableDemographicsu002ejsonu002epatientWakeUpTime" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    true
  )

  val MPowerEnrollmentSurvey = DataSetSetting.apply2(
    None,
    lux_park_mpower_enrollment_survey,
    "externalId",
    "_id",
    Seq("externalId", "dataGroups", "createdOn", "appVersion", "phoneInfo", "age", "gender", "race", "education", "employment", "years-smoking"),
    Seq("dataGroups", "gender", "education", "age"),
    3,
    "age",
    "years-smoking",
    "education" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    true
  )

  val MPowerPDEnrollmentSurvey = DataSetSetting.apply2(
    None,
    lux_park_mpower_pd_enrollment_survey,
    "externalId",
    "_id",
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "diagnosis-year", "onset-year", "deep-brain-stimulation"),
    Seq("dataGroups", "appVersion", "medication-bool", "diagnosis-year"),
    3,
    "ROW_ID",
    "ROW_ID",
    "medication-start-year" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    true
  )

  val MPowerTremorActivity = DataSetSetting.apply2(
    None,
    lux_park_mpower_tremor_activity,
    "externalId",
    "_id",
    Seq("dataGroups", "externalId", "createdOn", "momentInDayFormatu002ejsonu002echoiceAnswers", "appVersion", "deviceMotion_tremor_handAtShoulderLength_leftu002ejsonu002eitems"),
    Seq("dataGroups", "externalId", "appVersion", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    3,
    "ROW_ID",
    "ROW_ID",
    "phoneInfo" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )

  val MPowerWalkingActivity = DataSetSetting.apply2(
    None,
    lux_park_mpower_walking_activity,
    "externalId",
    "_id",
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "phoneInfo", "deviceMotion_walkingu002erestu002eitems"),
    Seq("dataGroups", "externalId", "appVersion", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    3,
    "ROW_ID",
    "ROW_ID",
    "momentInDayFormatu002ejsonu002echoiceAnswers" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )

  val MPowerTappingActivity = DataSetSetting.apply2(
    None,
    lux_park_mpower_tapping_activity,
    "externalId",
    "_id",
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "phoneInfo", "momentInDayFormatu002ejsonu002echoiceAnswers", "tapping_leftu002ejsonu002eTappingSamples", "tapping_rightu002ejsonu002eTappingSamples", "accel_tapping_rightu002ejsonu002eitems", "accel_tapping_leftu002ejsonu002eitems"),
    Seq("dataGroups", "externalId", "phoneInfo", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    3,
    "ROW_ID",
    "ROW_ID",
    "momentInDayFormatu002ejsonu002echoiceAnswers" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )

  val MPowerVoiceActivity = DataSetSetting.apply2(
    None,
    lux_park_mpower_voice_activity,
    "externalId",
    "_id",
    Seq("dataGroups", "externalId", "createdOn", "momentInDayFormatu002ejsonu002echoiceAnswers", "appVersion", "audio_audiou002em4a", "audio_countdownu002em4a"),
    Seq("dataGroups", "externalId", "appVersion", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    3,
    "ROW_ID",
    "ROW_ID",
    "appVersion" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )

  val MPowerMemoryActivity = DataSetSetting.apply2(
    None,
    lux_park_mpower_memory_activity,
    "externalId",
    "_id",
    Seq("dataGroups", "externalId", "createdOn", "momentInDayFormatu002ejsonu002echoiceAnswers", "appVersion", "MemoryGameResultsu002ejsonu002eMemoryGameNumberOfFailures", "MemoryGameResultsu002ejsonu002eMemoryGameNumberOfGames", "MemoryGameResultsu002ejsonu002eMemoryGameOverallScore", " MemoryGameResultsu002ejsonu002eMemoryGameGameRecords"),
    Seq("dataGroups", "momentInDayFormatu002ejsonu002echoiceAnswers", "MemoryGameResultsu002ejsonu002eMemoryGameNumberOfGames", "MemoryGameResultsu002ejsonu002eMemoryGameOverallScore"),
    3,
    "ROW_ID",
    "ROW_ID",
    "appVersion" ,
    None,
    Map(("\r", " "), ("\n", " ")),
    false
  )
}