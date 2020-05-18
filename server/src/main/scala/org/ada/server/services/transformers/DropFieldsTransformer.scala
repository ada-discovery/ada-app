package org.ada.server.services.transformers

import org.ada.server.AdaException
import org.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.ada.server.models.datatrans.DropFieldsTransformation
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private class DropFieldsTransformer extends AbstractDataSetTransformer[DropFieldsTransformation] {

  private val saveViewsAndFilters = false

  override protected def execInternal(
    spec: DropFieldsTransformation
  ) =
    for {
      // source DSA
      sourceDsa <- Future(dsaSafe(spec.sourceDataSetId))

      // check if the fields are specified correctly
      _ = if (spec.fieldNamesToKeep.nonEmpty && spec.fieldNamesToDrop.nonEmpty)
          throw new AdaException("Both 'fields to keep' and 'fields to drop' defined at the same time.")

      // get the fields to keep
      fieldsToKeep <- {
        // helper function to find fields
        def findFields(criterion: Option[Criterion[Any]] = None) =
          sourceDsa.fieldRepo.find(criterion.map(Seq(_)).getOrElse(Nil))

        if (spec.fieldNamesToKeep.nonEmpty)
          findFields(Some(FieldIdentity.name #-> spec.fieldNamesToKeep.toSeq))
        else if (spec.fieldNamesToDrop.nonEmpty)
          findFields(Some(FieldIdentity.name #!-> spec.fieldNamesToDrop.toSeq))
        else
          findFields()
      }

      // input data stream
      inputStream <- sourceDsa.dataSetRepo.findAsStream(projection = fieldsToKeep.map(_.name))

    } yield
      (sourceDsa, fieldsToKeep, inputStream, saveViewsAndFilters)
}