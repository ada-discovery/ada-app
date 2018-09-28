package runnables.luxpark

import javax.inject.Inject

import org.incal.core.dataaccess.Criterion._
import models.FieldTypeId
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import org.incal.core.InputFutureRunnable
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class FindNoNullZeroNumericFields @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnable[FindNoNullZeroNumericFieldsSpec] {

  private val numericFieldTypeIds = Seq(FieldTypeId.Double, FieldTypeId.Integer)

  private val logger = Logger

  override def runAsFuture(input: FindNoNullZeroNumericFieldsSpec) = {
    val dsa = dsaf(input.dataSetId).get

    for {
      numericFields <- dsa.fieldRepo.find(Seq("fieldType" #-> numericFieldTypeIds))

      jsons <- dsa.dataSetRepo.find(projection = numericFields.map(_.name))
    } yield {
      val foundFieldsWithZeroCount = numericFields.flatMap { field =>
        val values = jsons.flatMap(json => (json \ field.name).asOpt[Double])
        val zeroCount = values.count(_.equals(0d))

        // if all defined and at least one zero
        if (values.size == jsons.size && zeroCount >= input.minZeroCount)
          Some((field, zeroCount))
        else
          None
      }.toSeq.sortBy(_._2)

      val output = foundFieldsWithZeroCount.map { case (field, zeroCount) =>
        s"${field.name} (${field.label.getOrElse("")}) : $zeroCount"
      }.mkString("\n")

      logger.info(s"The number of fields with no null value and some zeroes is ${foundFieldsWithZeroCount.size}:")
      logger.info("\n" + output)
    }
  }

  override def inputType = typeOf[FindNoNullZeroNumericFieldsSpec]
}

case class FindNoNullZeroNumericFieldsSpec(
  dataSetId: String,
  minZeroCount: Int
)