package runnables.luxpark

import runnables.DataSetId._
import models.DataSetSetting

// just temporary, these settings should be provided through the data upload ui
object LuxParkDataSetSetting {

  val Luxpark = DataSetSetting(
    None,
    luxpark,
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

  val IBBL = DataSetSetting(
    None,
    ibbl,
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

  val IBBLTest = DataSetSetting(
    None,
    ibbl_test,
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
}