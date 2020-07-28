package org.ada.server.services.transformers

import org.ada.server.AdaException
import org.ada.server.models.datatrans.FilterDataSetTransformation
import org.ada.server.field.FieldUtil.toDataSetCriteria

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Transformer that filters a given data set by `filterId` attribute of [[FilterDataSetTransformation]] spec.
  * Note that `filterId` is a reference to an actual persisted filter, which must exist. This transformer doesn't preserve views or filters.
  *
  * Because the transformer is private, in order to execute it (as it's with all other transformers),
  * you need to obtain the central transformer [[org.ada.server.services.ServiceTypes.DataSetCentralTransformer]] through DI and pass a transformation spec as shown in an example bellow.
  *
  * Example:
  * {{{
  * // create a spec
  * val spec = FilterDataSetTransformation(
  *   sourceDataSetId = "covid_19.clinical_visit",
  *   filterId = BSONObjectID.parse("577e0caf8e00000a0193fd24"),
  *   fieldNamesToDrop = Nil,
  *   resultDataSetSpec = ResultDataSetSpec(
  *     "covid_19.clinical_visit_filtered",
  *     "Covid-19 Clinical Visit Filtered"
  *   )
  * )
  *
  * // execute
  * centralTransformer(spec)
  * }}}
  */
private class FilterDataSetTransformer extends AbstractDataSetTransformer[FilterDataSetTransformation] {

  private val saveViewsAndFilters = true

  override protected def execInternal(
    spec: FilterDataSetTransformation
  ) =
    for {
      // source data set accessor
      sourceDsa <- dsaWithNoDataCheck(spec.sourceDataSetId)

      // get a filter
      filter <- sourceDsa.filterRepo.get(spec.filterId)

      // check if the filter exists
      _ = if(filter.isEmpty)
        throw new AdaException(s"Filter '${spec.filterId.stringify}' cannot be found.")

      // turn filter's conditions into criteria
      criteria <- toDataSetCriteria(sourceDsa.fieldRepo, filter.get.conditions)

      // all the fields
      fields <- sourceDsa.fieldRepo.find()

      // input data stream
      inputStream <- sourceDsa.dataSetRepo.findAsStream(criteria)
    } yield
      (sourceDsa, fields, inputStream, saveViewsAndFilters)
}