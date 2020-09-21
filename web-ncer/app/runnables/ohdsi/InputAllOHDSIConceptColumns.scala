package runnables.ohdsi

import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.runnables.InputFutureRunnableExt
import org.incal.core.util.seqFutures
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InputAllOHDSIConceptColumns @Inject() (
  dsaf: DataSetAccessorFactory,
  inputColumn: InputOHDSIColumnConcepts
) extends InputFutureRunnableExt[InputAllOHDSIConceptColumnsSpec] {

  private val logger = Logger

  override def runAsFuture(input: InputAllOHDSIConceptColumnsSpec) =
    for {
      // data set accessor
      dsa <- dsaf.getOrError(input.targetDataSetId.trim)

      // get all the fields for a given data set
      fields <- dsa.fieldRepo.find()

      conceptFields = fields.filter(_.name.contains("concept"))

      _ <- seqFutures(conceptFields) { field =>
        logger.info(s"OHDSI concept inputting for the data set '${input.targetDataSetId}' and the field '${field.name}' started.")
        inputColumn.runAsFuture(InputOHDSIColumnConceptsSpec(
          ohdsiConceptDataSetId = input.ohdsiConceptDataSetId,
          targetDataSetId = input.targetDataSetId,
          targetFieldName = field.name
        )).recover {
          case e: Exception =>
            logger.error(s"Processing of a concept field '${field.name}' failed", e)
            ()
        }
      }
    } yield
      ()
}

case class InputAllOHDSIConceptColumnsSpec(
  ohdsiConceptDataSetId: String,
  targetDataSetId: String
)