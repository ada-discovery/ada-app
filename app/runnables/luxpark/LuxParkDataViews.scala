package runnables.luxpark

import models.{DataView, DataSetSetting}
import runnables.DataSetId._

object LuxParkDataViews{

  val Clinical = DataView.applyMain(
    Seq("cdisc_dm_usubjd", "redcap_event_name", "cdisc_dm_subjid_2", "dm_site", "cdisc_dm_brthdtc", "cdisc_dm_sex", "cdisc_sc_sctestcd_maritstat"),
    Seq("cdisc_dm_sex", "control_q1", "cdisc_sc_sctestcd_maritstat", "sv_age"),
    3,
    false
  )

  val IBBLBiosamples = DataView.applyMain(
    Seq("sampleid", "sampletypeid", "kittype", "storagestatus", "qtycurrent"),
    Seq("sampletypeid", "kittype", "storagestatus", "qtycurrent"),
    3,
    false
  )

  val IBBLBiosampleTests = DataView.applyMain(
    Seq("sampleid", "sstudyid", "testapproval", "paramdesc", "testdesc", "createdt"),
    Seq("paramdesc", "testapproval", "teststatus", "paramlistdesc", "datatypes", "paramid"),
    3,
    false
  )

  val MPowerMyThoughts = DataView.applyMain(
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "feeling_better", "feeling_worse"),
    Seq("dataGroups", "externalId", "appVersion", "feeling_worse"),
    3,
    false
  )

  val MPowerDemographics = DataView.applyMain(
    Seq("dataGroups", "externalId", "NonIdentifiableDemographicsu002ejsonu002epatientHeightInches", "NonIdentifiableDemographicsu002ejsonu002epatientWeightPounds"),
    Seq("dataGroups", "externalId", "NonIdentifiableDemographicsu002ejsonu002epatientHeightInches", "NonIdentifiableDemographicsu002ejsonu002epatientWeightPounds"),
    3,
    false
  )

  val MPowerEnrollmentSurvey = DataView.applyMain(
    Seq("externalId", "dataGroups", "createdOn", "appVersion", "phoneInfo", "age", "gender", "race", "education", "employment", "years-smoking"),
    Seq("dataGroups", "gender", "education", "age"),
    3,
    false
  )

  val MPowerPDEnrollmentSurvey = DataView.applyMain(
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "diagnosis-year", "onset-year", "deep-brain-stimulation"),
    Seq("dataGroups", "appVersion", "medication-bool", "diagnosis-year"),
    3,
    false
  )

  val MPowerTremorActivity = DataView.applyMain(
    Seq("dataGroups", "externalId", "createdOn", "momentInDayFormatu002ejsonu002echoiceAnswers", "appVersion", "deviceMotion_tremor_handAtShoulderLength_leftu002ejsonu002eitems"),
    Seq("dataGroups", "externalId", "appVersion", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    3,
    false
  )

  val MPowerWalkingActivity = DataView.applyMain(
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "phoneInfo", "deviceMotion_walkingu002erestu002eitems"),
    Seq("dataGroups", "externalId", "appVersion", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    3,
    false
  )

  val MPowerTappingActivity = DataView.applyMain(
    Seq("dataGroups", "externalId", "createdOn", "appVersion", "phoneInfo", "momentInDayFormatu002ejsonu002echoiceAnswers", "tapping_leftu002ejsonu002eTappingSamples", "tapping_rightu002ejsonu002eTappingSamples", "accel_tapping_rightu002ejsonu002eitems", "accel_tapping_leftu002ejsonu002eitems"),
    Seq("dataGroups", "externalId", "phoneInfo", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    3,
    false
  )

  val MPowerVoiceActivity = DataView.applyMain(
    Seq("dataGroups", "externalId", "createdOn", "momentInDayFormatu002ejsonu002echoiceAnswers", "appVersion", "audio_audiou002em4a", "audio_countdownu002em4a"),
    Seq("dataGroups", "externalId", "appVersion", "momentInDayFormatu002ejsonu002echoiceAnswers"),
    3,
    false
  )

  val MPowerMemoryActivity = DataView.applyMain(
    Seq("dataGroups", "externalId", "createdOn", "momentInDayFormatu002ejsonu002echoiceAnswers", "appVersion", "MemoryGameResultsu002ejsonu002eMemoryGameNumberOfFailures", "MemoryGameResultsu002ejsonu002eMemoryGameNumberOfGames", "MemoryGameResultsu002ejsonu002eMemoryGameOverallScore", " MemoryGameResultsu002ejsonu002eMemoryGameGameRecords"),
    Seq("dataGroups", "momentInDayFormatu002ejsonu002echoiceAnswers", "MemoryGameResultsu002ejsonu002eMemoryGameNumberOfGames", "MemoryGameResultsu002ejsonu002eMemoryGameOverallScore"),
    3,
    false
  )
}