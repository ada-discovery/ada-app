package org.ada.server.services.transformers

import org.ada.server.models.datatrans.CopyDataSetTransformation

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private class CopyDataSetTransformer extends AbstractDataSetTransformer[CopyDataSetTransformation] {

  private val saveViewsAndFilters = true

  override protected def execInternal(
    spec: CopyDataSetTransformation
  ) =
    for {
      sourceDsa <- Future(dsaSafe(spec.sourceDataSetId))

      // all the fields
      fields <- sourceDsa.fieldRepo.find()

      // input data stream
      inputStream <- sourceDsa.dataSetRepo.findAsStream()
    } yield
      (sourceDsa, fields, inputStream, saveViewsAndFilters)
}