package runnables.core

import dataaccess.RepoTypes.{FieldRepo, JsonCrudRepo}
import dataaccess.{FieldType, FieldTypeHelper}
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{AdaException, Field, FieldTypeId}
import play.api.libs.json.{JsNull, JsString, JsValue, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import runnables.DsaInputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class ReplaceString extends DsaInputFutureRunnable[ReplaceStringSpec] {

  private val idName = JsObjectIdentity.name
  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(spec: ReplaceStringSpec) = {
    val dsa_ = dsa(spec.dataSetId)

    for {
      // field
      fieldOption <- dsa_.fieldRepo.get(spec.fieldName)
      field = fieldOption.getOrElse(throw new AdaException(s"Field ${spec.fieldName} not found."))
      fieldType = ftf(field.fieldTypeSpec)

      // replace for String or Enum
      _ <- field.fieldTypeSpec.fieldType match {
        case FieldTypeId.String => replaceForString(dsa_.dataSetRepo, fieldType.asValueOf[String], spec)
        case FieldTypeId.Enum => replaceForEnum(dsa_.fieldRepo, field, spec)
        case _ => throw new AdaException(s"Comma-to-dot conversion is possible only for String and Enum types but got ${field.fieldTypeSpec}.")
      }
    } yield
      ()
  }

  private def replaceForString(
    repo: JsonCrudRepo,
    fieldType: FieldType[String],
    spec: ReplaceStringSpec
  ) = {
    for {
      // jsons
      idJsons <- repo.find(projection = Seq(idName, spec.fieldName))

      // get the records as String and replace
      idReplacedStringValues = idJsons.map { json =>
        val id = (json \ JsObjectIdentity.name).as[BSONObjectID]
        val replacedStringValue = fieldType.jsonToValue(json \ spec.fieldName).map(_.replaceAllLiterally(spec.from, spec.to))
        (id, replacedStringValue)
      }

      // replace the values and update the records
      _ <- util.seqFutures(idReplacedStringValues.toSeq.grouped(spec.batchSize)) { group =>
        for {
          newJsons <- Future.sequence(
            group.map { case (id, replacedStringValue) =>
              repo.get(id).map { json =>
                val jsValue: JsValue = replacedStringValue.map(JsString(_)).getOrElse(JsNull)
                json.get ++ Json.obj(spec.fieldName -> jsValue)
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
    field: Field,
    spec: ReplaceStringSpec
  ) = {
    val newNumValue = field.numValues.map(_.map { case (key, value) =>
      (key, value.replaceAllLiterally(spec.from, spec.to))
    })

    repo.update(field.copy(numValues = newNumValue))
  }

  override def inputType = typeOf[ReplaceStringSpec]
}

case class ReplaceStringSpec(dataSetId: String, fieldName: String, batchSize: Int, from: String, to: String)