package services

import models.{Category => CCategory}

object DeNoPaTranSMARTMapping {

  val demographics = CCategory("Demographics")
  val mmseTest = CCategory("PSP. MMSE (Folstein Mini-Mental State Exam)")
  val clockDrawingTest = CCategory("A. Clock Drawing Test")
  val mdsUPDRSTest = CCategory("A. MDS-UPDRS - Part I to Part IV")
  val hYTest = CCategory("A. H&Y (Hoehn and Yahr")
  val nmsPDTest = CCategory("A. NMS-PD (Non-Movement Problems in Parkinson's)")
  val moCaTest = CCategory("A. MoCA (Montreal Cognitive Assessment Test)")
  val pdssTest = CCategory("A. PDSS (Parkinson's Disease Sleep Scale)")
  val essTest = CCategory("ESS (Epworth Sleepiness Scale) - UNUSED")
  val remSleepTest = CCategory("Similar (13 Qs): A. REM Sleep Behavior Disorder Screening Questionnaire")

  val rootCategory = {
    val root = CCategory("")
    root.addChildren(List(demographics, mmseTest, clockDrawingTest, mdsUPDRSTest, hYTest, nmsPDTest, moCaTest, pdssTest, essTest, remSleepTest))
    root
  }

  val fieldsCategoryMap = Map(
    "Probanden_Nr" -> demographics,
    "Geb_Datum" -> demographics,
    "Geschlecht" -> demographics,
    "a_CRF_Schuljahre" -> demographics,
    "a_CRF_MMST_1_1" -> mmseTest,
    "a_CRF_MMST_1_2" -> mmseTest,
    "a_CRF_MMST_1_3" -> mmseTest,
    "a_CRF_MMST_1_4" -> mmseTest,
    "a_CRF_MMST_1_5" -> mmseTest,
    "a_CRF_MMST_2_1" -> mmseTest,
    "a_CRF_MMST_2_2" -> mmseTest,
    "a_CRF_MMST_2_3" -> mmseTest,
    "a_CRF_MMST_2_4" -> mmseTest,
    "a_CRF_MMST_2_5" -> mmseTest,
    "a_CRF_MMST_3" -> mmseTest,
    "a_CRF_MMST_4" -> mmseTest,
    "a_CRF_MMST_5" -> mmseTest,
    "a_CRF_MMST_6" -> mmseTest,
    "a_CRF_MMST_7" -> mmseTest,
    "a_CRF_MMST_8" -> mmseTest,
    "a_CRF_MMST_9" -> mmseTest,
    "a_CRF_MMST_10" -> mmseTest,
    "a_CRF_MMST_11" -> mmseTest,
    "a_CRF_MMST_Summe" -> mmseTest,
    "a_CRF_Uhrentest" -> clockDrawingTest
  )
}
