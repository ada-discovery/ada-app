package org.ada.server.services.transformers

import org.ada.server.field.FieldType
import org.ada.server.field.FieldUtil.FieldOps
import org.ada.server.models.datatrans.ChangeFieldTypesDataSetTransformation
import play.api.libs.json.{JsObject, JsReadable, JsValue}

import scala.concurrent.ExecutionContext.Implicits.global

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
