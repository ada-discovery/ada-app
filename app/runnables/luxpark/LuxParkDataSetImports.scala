package runnables.luxpark

import javax.inject.Inject
import play.api.Configuration
import models._
import runnables.DataSetId._

class LuxParkDataSetImports @Inject() (configuration: Configuration) {

  val list = Seq(

    RedCapDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_clinical,
      "Clinical",
      configuration.getString("redcap.prodserver.api.url").get,
      configuration.getString("redcap.prodserver.token").get,
      true,
      false,
      None,
      Some(LuxParkDataSetSettings.Clinical)
    ),

    CsvDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_ibbl_biosamples,
      "IBBL Biosamples",
      Some(configuration.getString("ibbl.import.folder").get + "140174_ND_STOCK_LCSB_20160404.csv"),
      ",",
      None,
      None,
      false,
      None,
      Some(LuxParkDataSetSettings.IBBLBiosamples)
    ),

    CsvDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_ibbl_biosample_tests,
      "IBBL Biosample Tests",
      Some(configuration.getString("ibbl.import.folder").get + "140174_ND_TEST_LCSB_20160404.csv"),
      ",",
      None,
      Some("ISO-8859-1"),
      false,
      None,
      Some(LuxParkDataSetSettings.IBBLBiosampleTests)
    ),

    SynapseDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_mpower_my_thoughts,
      "mPower My Thoughts",
      "syn6130514",
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerMyThoughts)
    ),

    SynapseDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_mpower_demographics,
      "mPower Demographics",
      "syn6130512",
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerDemographics)
    ),

    SynapseDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_mpower_enrollment_survey,
      "mPower Enrollment Survey",
      "syn6128276",
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerEnrollmentSurvey)
    ),

    SynapseDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_mpower_pd_enrollment_survey,
      "mPower PD Enrollment Survey",
      "syn6130511",
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerPDEnrollmentSurvey)
    ),

    SynapseDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_mpower_tremor_activity,
      "mPower Tremor Activity",
      "syn6128278",
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerTremorActivity)
    ),

    SynapseDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_mpower_walking_activity,
      "mPower Walking Activity",
      "syn6128279",
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerWalkingActivity)
    ),

    SynapseDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_mpower_tapping_activity,
      "mPower Tapping Activity",
      "syn6130513",
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerTappingActivity)
    ),

    SynapseDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_mpower_voice_activity,
      "mPower Voice Activity",
      "syn6128277",
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerVoiceActivity)
    ),

    SynapseDataSetImportInfo(
      None,
      "Lux Park",
      lux_park_mpower_memory_activity,
      "mPower Memory Activity",
      "syn6126230",
      false,
      None,
      Some(LuxParkDataSetSettings.MPowerMemoryActivity)
    )
  )
}