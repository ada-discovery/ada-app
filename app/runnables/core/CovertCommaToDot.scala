package runnables.core

import dataaccess.RepoTypes.{FieldRepo, JsonCrudRepo}
import dataaccess.{FieldType, FieldTypeHelper}
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{AdaException, Field, FieldTypeId}
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import runnables.DsaInputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class ConvertCommaToDot extends DsaInputFutureRunnable[ConvertCommaToDotSpec] {

  private val idName = JsObjectIdentity.name

  private val ftf = FieldTypeHelper.fieldTypeFactory()
  private val fti = FieldTypeHelper.fieldTypeInferrer

  override def runAsFuture(spec: ConvertCommaToDotSpec) = {
    val dsa_ = dsa(spec.dataSetId)

    for {
      // field
      fieldOption <- dsa_.fieldRepo.get(spec.fieldName)
      field = fieldOption.getOrElse(throw new AdaException(s"Field ${spec.fieldName} not found."))
      fieldType = ftf(field.fieldTypeSpec)

      // replace for String or Enum
      _ <- field.fieldTypeSpec.fieldType match {
        case FieldTypeId.String => replaceForString(dsa_.dataSetRepo, fieldType, spec)
        case FieldTypeId.Enum => replaceForEnum(dsa_.fieldRepo, field)
        case _ => throw new AdaException(s"Comma-to-dot conversion is possible only for String and Enum types but got ${field.fieldTypeSpec}.")
      }
    } yield
      ()
  }

  private def replaceForString(
    repo: JsonCrudRepo,
    fieldType: FieldType[_],
    spec: ConvertCommaToDotSpec
  ) = {
    for {
      // jsons
      idJsons <- repo.find(projection = Seq(JsObjectIdentity.name, spec.fieldName))

      // get the records as String and replace '.' -> '.'
      idReplacedStringValues = idJsons.map { json =>
        val id = (json \ JsObjectIdentity.name).as[BSONObjectID]
        val stringValue = fieldType.jsonToDisplayString(json \ spec.fieldName)
        val replacedStringValue = stringValue.replace(',', '.')
        (id, replacedStringValue)
      }

      // new type
      newFieldType = fti(idReplacedStringValues.map(_._2))

      // replace the values and update the records
      _ <- util.seqFutures(idReplacedStringValues.toSeq.grouped(spec.batchSize)) { group =>
        for {
          newJsons <- Future.sequence(
            group.map { case (id, replacedStringValue) =>
              repo.get(id).map { json =>
                val newJsonValue = newFieldType.displayStringToJson(replacedStringValue)
                json.get ++ Json.obj(spec.fieldName -> newJsonValue)
              }
            }
          )

          _ <- repo.update(newJsons)
        } yield
          ()
      }
    } yield
      ()
  }

  private def replaceForEnum(
    repo: FieldRepo,
    field: Field
  ) = {
    val newNumValue = field.numValues.map(_.map { case (key, value) =>
      (key, value.replace(',', '.'))
    })

    repo.update(field.copy(numValues = newNumValue))
  }

  override def inputType = typeOf[ConvertCommaToDotSpec]
}

case class ConvertCommaToDotSpec(dataSetId: String, fieldName: String, batchSize: Int)