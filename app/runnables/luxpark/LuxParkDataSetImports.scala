package runnables.luxpark

import javax.inject.Inject
import play.api.Configuration
import models._
import runnables.DataSetId._

class LuxParkDataSetImports @Inject() (configuration: Configuration) {

  val list = Seq(

    RedCapDataSetImport(
      None,
      "Lux Park",
      lux_park_clinical,
      "Clinical Visit",
      configuration.getString("redcap.prodserver.api.url").get,
      configuration.getString("redcap.prodserver.token").get,
      true,
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.Clinical),
      Some(LuxParkDataViews.Clinical)
    ),

    CsvDataSetImport(
      None,
      "Lux Park",
      lux_park_ibbl_biosamples,
      "IBBL Biosamples",
      Some(configuration.getString("ibbl.import.folder").get + "140174_ND_STOCK_LCSB_20160701102203.csv"),
      ";",
      None,
      None,
      true,
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.IBBLBiosamples),
      Some(LuxParkDataViews.IBBLBiosamples)
    ),

    CsvDataSetImport(
      None,
      "Lux Park",
      lux_park_ibbl_biosample_tests,
      "IBBL Biosample Tests",
      Some(configuration.getString("ibbl.import.folder").get + "140174_ND_TEST_LCSB_20160701102325.csv"),
      ";",
      None,
      Some("ISO-8859-1"),
      true,
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.IBBLBiosampleTests),
      Some(LuxParkDataViews.IBBLBiosampleTests)
    ),

    SynapseDataSetImport(
      None,
      "Lux Park",
      lux_park_mpower_my_thoughts,
      "mPower My Thoughts",
      "syn6130514",
      true,
      None,
      None,
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerMyThoughts),
      Some(LuxParkDataViews.MPowerMyThoughts)
    ),

    SynapseDataSetImport(
      None,
      "Lux Park",
      lux_park_mpower_demographics,
      "mPower Demographics",
      "syn6130512",
      true,
      None,
      None,
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerDemographics),
      Some(LuxParkDataViews.MPowerDemographics)
    ),

    SynapseDataSetImport(
      None,
      "Lux Park",
      lux_park_mpower_enrollment_survey,
      "mPower Enrollment Survey",
      "syn6128276",
      true,
      None,
      None,
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerEnrollmentSurvey),
      Some(LuxParkDataViews.MPowerEnrollmentSurvey)
    ),

    SynapseDataSetImport(
      None,
      "Lux Park",
      lux_park_mpower_pd_enrollment_survey,
      "mPower PD Enrollment Survey",
      "syn6130511",
      true,
      None,
      None,
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerPDEnrollmentSurvey),
      Some(LuxParkDataViews.MPowerPDEnrollmentSurvey)
    ),

    SynapseDataSetImport(
      None,
      "Lux Park",
      lux_park_mpower_tremor_activity,
      "mPower Tremor Activity",
      "syn6128278",
      true,
      Some(10),
      Some(3),
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerTremorActivity),
      Some(LuxParkDataViews.MPowerTremorActivity)
    ),

    SynapseDataSetImport(
      None,
      "Lux Park",
      lux_park_mpower_walking_activity,
      "mPower Walking Activity",
      "syn6128279",
      true,
      Some(20),
      Some(3),
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerWalkingActivity),
      Some(LuxParkDataViews.MPowerWalkingActivity)
    ),

    SynapseDataSetImport(
      None,
      "Lux Park",
      lux_park_mpower_tapping_activity,
      "mPower Tapping Activity",
      "syn6130513",
      true,
      None,
      None,
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerTappingActivity),
      Some(LuxParkDataViews.MPowerTappingActivity)
    ),

    SynapseDataSetImport(
      None,
      "Lux Park",
      lux_park_mpower_voice_activity,
      "mPower Voice Activity",
      "syn6128277",
      false,
      None,
      None,
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerVoiceActivity),
      Some(LuxParkDataViews.MPowerVoiceActivity)
    ),

    SynapseDataSetImport(
      None,
      "Lux Park",
      lux_park_mpower_memory_activity,
      "mPower Memory Activity",
      "syn6126230",
      true,
      None,
      None,
      false,
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerMemoryActivity),
      Some(LuxParkDataViews.MPowerMemoryActivity)
    )
  )
}