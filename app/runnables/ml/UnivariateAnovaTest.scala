package runnables.ml

import java.{lang => jl}
import javax.inject.Inject

import dataaccess.Criterion._
import dataaccess.{FieldType, FieldTypeHelper, NotEqualsNullCriterion}
import models.DataSetFormattersAndIds.FieldIdentity
import persistence.dataset.DataSetAccessorFactory
import runnables.FutureRunnable
import services.SparkApp
import services.stats.StatsService

import scala.concurrent.ExecutionContext.Implicits.global

class UnivariateAnovaTest @Inject()(
    dsaf: DataSetAccessorFactory,
    statsService: StatsService,
    sparkApp: SparkApp
  ) extends FutureRunnable {

  private val doubleFieldNames = Seq(
    "pdss_score",
    "status_bmi",
    "status_height",
    "sv_age",
    "clock_score",
    "rem_score",
    "sniff_score",
    "u_part1_score",
    "u_part2_score",
    "u_part3_score",
    "u_part4_score",
    "fds_score",
    "fesi_score",
    "mayo_score",
    "mdt_score"
  )

  private val categoricalFieldName = "control_q1"
  private val fieldNames = doubleFieldNames ++ Seq(categoricalFieldName)

  override def runAsFuture = {
    val dsa = dsaf("lux_park.clinical").get

    for {
      numericalFields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> doubleFieldNames))
      categoricalField <- dsa.fieldRepo.get(categoricalFieldName)
      jsons <- dsa.dataSetRepo.find(criteria = Seq(NotEqualsNullCriterion(categoricalFieldName)), projection = fieldNames)
    } yield {
      val numericalFieldsSeq = numericalFields.toSeq
      val pValues = statsService.testOneWayAnova(jsons, numericalFieldsSeq, categoricalField.get)
      numericalFieldsSeq.zip(pValues).foreach { case (field, pValue) =>
        println(field.name + " : " + pValue)
      }
    }
  }
}