package runnables

import javax.inject.Inject

import dataaccess.JsonRepoExtra._
import models.FieldTypeId
import persistence.dataset.DataSetAccessorFactory
import org.incal.core.dataaccess.Criterion.Infix
import play.api.Logger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class FrancescosRun @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnable[FrancescosRunSpec] {

  private val logger = Logger

  override def runAsFuture(input: FrancescosRunSpec) = {
    val dsa = dsaf(input.dataSetId).getOrElse(
      throw new IllegalArgumentException(s"Data set ${input.dataSetId} not found")
    )

    for {
      // total count
      count <- dsa.dataSetRepo.count()

      // total field count
      fieldCount <- dsa.fieldRepo.count()

      // retrieve all the double fields
      doubleFields <- dsa.fieldRepo.find(Seq("fieldType" #== FieldTypeId.Double))

      // get the min value of a given field
      minValue <- dsa.dataSetRepo.min(input.fieldName, addNotNullCriterion = true)

      // get the max value of a given field
      maxValue <- dsa.dataSetRepo.max(input.fieldName, addNotNullCriterion = true)
    } yield {

      logger.info(s"Item/field count: $count/$fieldCount")

      val doubleFieldNamesString = doubleFields.map(_.name).mkString(", ")
      logger.info(s"Double fields: $doubleFieldNamesString")

      logger.info(s"Min/max value of ${input.fieldName}: $minValue/$maxValue.")
    }
  }

  override def inputType = typeOf[FrancescosRunSpec]
}

case class FrancescosRunSpec(
  dataSetId: String,
  fieldName: String
)

object FrancescosRun extends GuiceBuilderRunnable[FrancescosRun] with App { run }
