package runnables.luxpark

import javax.inject.Inject
import org.incal.core.dataaccess.Criterion._
import org.ada.server.models.FieldTypeId
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class FindNoNullZeroNumericFields @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnableExt[FindNoNullZeroNumericFieldsSpec] {

  private val numericFieldTypeIds = Seq(FieldTypeId.Double, FieldTypeId.Integer)

  private val logger = Logger

  override def runAsFuture(input: FindNoNullZeroNumericFieldsSpec) =
    for {
      // data set accessor
      dsa <- dsaf.getOrError(input.dataSetId)

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

case class FindNoNullZeroNumericFieldsSpec(
  dataSetId: String,
  minZeroCount: Int
)