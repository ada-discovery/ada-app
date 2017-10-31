package runnables.ml

import javax.inject.Inject

import dataaccess.NotEqualsNullCriterion
import dataaccess.Criterion._
import models.DataSetFormattersAndIds.FieldIdentity
import persistence.dataset.DataSetAccessorFactory
import runnables.FutureRunnable
import services.{SparkApp, StatsService}
import scala.concurrent.ExecutionContext.Implicits.global

class ChiSquareTest @Inject()(
    dsaf: DataSetAccessorFactory,
    statsService: StatsService
  ) extends FutureRunnable {

  private val inputFieldNames = Seq(
    "cdisc_dm_sex",
    "cdisc_sc_sctestcd_maritstat",
    "dm_site",
    "dm_language_1",
    "dm_language_2"
  )

  private val targetFieldName = "control_q1"
  private val fieldNames = inputFieldNames ++ Seq(targetFieldName)

  override def runAsFuture = {
    val dsa = dsaf("lux_park.clinical").get

    for {
      inputFields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> inputFieldNames))
      targetField <- dsa.fieldRepo.get(targetFieldName)
      jsons <- dsa.dataSetRepo.find(criteria = Seq(NotEqualsNullCriterion(targetFieldName)), projection = fieldNames)
    } yield {
      val inputFieldsSeq = inputFields.toSeq
      val results = statsService.testChiSquare(jsons, inputFieldsSeq, targetField.get)

      inputFieldsSeq.zip(results).foreach { case (field, result) =>
        println(s"Field: ${field.name} =>      pValue = ${result.pValue}, degree of freedom = ${result.degreeOfFreedom}, statistics = ${result.statistics}")
      }
    }
  }
}