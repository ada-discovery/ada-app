package org.ada.server.services.transformers

import org.ada.server.field.FieldType
import org.ada.server.field.FieldUtil.FieldOps
import org.ada.server.models.datatrans.ChangeFieldTypesDataSetTransformation
import play.api.libs.json.{JsObject, JsReadable, JsValue}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Transformer that changes field types of a given data set defined by an attribute `newFields` of [[ChangeFieldTypesDataSetTransformation]] spec.
  * This results in creating a new data set where values of the affected fields are re-parsed and stored with new types.
  *
  * Because the transformer is private, in order to execute it (as it's with all other transformers),
  * you need to obtain the central transformer [[org.ada.server.services.ServiceTypes.DataSetCentralTransformer]] through DI and pass a transformation spec as shown in an example bellow.
  *
  * Example:
  * {{{
  * // create a spec
  * val spec = ChangeFieldTypesDataSetTransformation(
  *   sourceDataSetId = "covid_19.clinical_visit",
  *   newFields = Seq(
  *     Field(name = "gender", fieldType = FieldTypeId.String),
  *     Field(name = "visit", fieldType = FieldTypeId.Enum, enumValues = Map("1" -> "Visit 1", "2" -> "Visit 2")
  *   ),
  *   resultDataSetSpec = ResultDataSetSpec(
  *     "covid_19.clinical_visit_types_changed",
  *     "Covid-19 Clinical Visit w. Types Changed"
  *   )
  * )
  *
  * // execute
  * centralTransformer(spec)
  * }}}
  *
  * @see relates to [[InferDataSetTransformer]]
  */
private class ChangeFieldTypesDataSetTransformer extends AbstractDataSetTransformer[ChangeFieldTypesDataSetTransformation] {

  private val saveViewsAndFilters = true

  override protected def execInternal(
    spec: ChangeFieldTypesDataSetTransformation
  ) =
    for {
      // source data set accessor
      sourceDsa <- dsaWithNoDataCheck(spec.sourceDataSetId)

      // input data stream
      inputStream <- sourceDsa.dataSetRepo.findAsStream()

      // transform the stream by applying inferred types and converting jsons
      newFieldNameAndTypeMap = spec.newFields.map(_.toNamedTypeAny).toMap

      // final transformed stream
      transformedStream = inputStream.map { json =>
        val newJsonValues = json.fields.map { case (fieldName, jsonValue) =>
          val newJsonValue = newFieldNameAndTypeMap.get(fieldName) match {
            case Some(newFieldType) => displayJsonToJson(newFieldType, jsonValue)
            case None => jsonValue
          }
          (fieldName, newJsonValue)
        }
        JsObject(newJsonValues)
      }

    } yield
      (sourceDsa, spec.newFields, transformedStream, saveViewsAndFilters)

  private def displayJsonToJson[T](
    fieldType: FieldType[T],
    json: JsReadable
  ): JsValue = {
    val value = fieldType.displayJsonToValue(json)
    fieldType.valueToJson(value)
  }
}
