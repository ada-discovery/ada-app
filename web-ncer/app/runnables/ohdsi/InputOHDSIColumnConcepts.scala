package runnables.ohdsi

import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.field.FieldUtil.FieldOps
import org.ada.server.models.FieldTypeId
import org.incal.core.dataaccess.{NotEqualsNullCriterion, NotInCriterion}
import org.incal.core.runnables.InputFutureRunnableExt
import org.incal.core.dataaccess.Criterion._
import play.api.Logger
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InputOHDSIColumnConcepts @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnableExt[InputOHDSIColumnConceptsSpec] {

  private val logger = Logger

  implicit val ohdsiJsonFormat = Json.format[OHDSIConcept]

  override def runAsFuture(input: InputOHDSIColumnConceptsSpec) = {
    val ohdsiConceptDsa = dsaSafe(input.ohdsiConceptDataSetId.trim)
    val targetDsa = dsaSafe(input.targetDataSetId.trim)
    val targetFieldName = input.targetFieldName.trim

    def nextValue(excludedValues: Seq[Int]) = {
      val exclusionCriterion = if (excludedValues.nonEmpty) Seq(targetFieldName #!-> excludedValues) else Nil

      for {
        jsObject <- targetDsa.dataSetRepo.find(
          criteria = Seq(NotEqualsNullCriterion(targetFieldName)) ++ exclusionCriterion,
          projection = Seq(targetFieldName),
          limit = Some(1)
        ).map(_.headOption)
      } yield
        jsObject.map { jsObject =>
          val jsValue = (jsObject \ targetFieldName)
          jsValue.asOpt[Int] match {
            case Some(int) => int
            case None => throw new AdaException(s"The target field ${targetFieldName} contains a non-integer code ${jsValue}.")
          }
        }
    }

    def collectDistinctValues(excludedValues: Seq[Int]): Future[Seq[Int]] =
      for {
        newValue <- nextValue(excludedValues)
        values <- newValue match {
          case Some(value) =>
            logger.debug(s"Found a value '${value}' for the target field ${targetFieldName}.")
            collectDistinctValues(excludedValues ++ Seq(value))
          case None =>
            Future(excludedValues)
        }
      } yield
        values

    for {
      // get a required target field
      targetField <- targetDsa.fieldRepo.get(targetFieldName)

      // check if exists and has a correct type
      _ = require(
        targetField.map(field => field.isDouble || field.isInteger).getOrElse(false),
        s"The target field ${targetFieldName} must exist and must be either integer or double."
      )

      // collect all distinct values for the target field
      distinctValues <- collectDistinctValues(Nil)

      // retrieve OHDSI concepts for the values/codes
      ohdsiConceptJsons <- {
        logger.info(s"Found ${distinctValues.size} distinct values for the target field ${targetFieldName}.")
        ohdsiConceptDsa.dataSetRepo.find(Seq("code" #-> distinctValues))
      }
      ohdsiConcepts = ohdsiConceptJsons.map(_.as[OHDSIConcept])

      // check if we got all the concepts
      _ = require(
        ohdsiConcepts.size == distinctValues.size,
        s"Some concepts could not be found ${distinctValues.toSet.diff(ohdsiConcepts.map(_.code).toSet).mkString(", ")}."
      )

      // change the field type to enum and set the ohdsi code-labels as enum values
      targetFieldToUpdate = targetField.get.copy(
        fieldType = FieldTypeId.Enum,
        enumValues = ohdsiConcepts.map(concept => (s"${concept.code}", concept.label)).toMap
      )

      // update the field
      _ <- targetDsa.fieldRepo.update(targetFieldToUpdate)
    } yield
      ()
  }

  private def dsaSafe(dataSetId: String) =
    dsaf.applySync(dataSetId).getOrElse(throw new AdaException(s"Data set ${dataSetId} not found"))
}

case class OHDSIConcept(
  code: Int,
  label: String
)

case class InputOHDSIColumnConceptsSpec(
  ohdsiConceptDataSetId: String,
  targetDataSetId: String,
  targetFieldName: String
)