package runnables.core

import dataaccess.RepoTypes.{FieldRepo, JsonCrudRepo}
import field.{FieldType, FieldTypeHelper}
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{AdaException, Field, FieldTypeId}
import org.incal.core.util.seqFutures
import play.api.libs.json.{JsNull, JsString, JsValue, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import runnables.DsaInputFutureRunnable
import util.FieldUtil.{InfixFieldOps, JsonFieldOps, NamedFieldType}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class ReplaceNumber extends DsaInputFutureRunnable[ReplaceNumberSpec] {

  private val idName = JsObjectIdentity.name

  override def runAsFuture(spec: ReplaceNumberSpec) = {
    val dsa = createDsa(spec.dataSetId)

    for {
      // field
      fieldOption <- dsa.fieldRepo.get(spec.fieldName)
      field = fieldOption.getOrElse(throw new AdaException(s"Field ${spec.fieldName} not found."))

      // replace for String or Enum
      _ <- if (!field.isArray && (field.isNumeric || field.isEnum)) {

      } else {
        throw new AdaException(s"Number replacement is possible only for double, integer, date, an enum types but got ${field.fieldTypeSpec}.")
      }
    } yield
      ()
  }

  private def replaceForString(
    repo: JsonCrudRepo,
    fieldType: NamedFieldType[String],
    spec: ReplaceNumberSpec
  ) = {
    for {
      // jsons
      idJsons <- repo.find(projection = Seq(idName, spec.fieldName))

      // get the records as String and replace
      idReplacedStringValues = idJsons.map { json =>
        val id = (json \ JsObjectIdentity.name).as[BSONObjectID]
        val replacedStringValue = json.toValue(fieldType).map(_.replaceAllLiterally(spec.from, spec.to))
        (id, replacedStringValue)
      }

      // replace the values and update the records
      _ <- seqFutures(idReplacedStringValues.toSeq.grouped(spec.batchSize)) { group =>
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
    spec: ReplaceNumberSpec
  ) = {
    val newNumValue = field.numValues.map(_.map { case (key, value) => (key, value.replaceAllLiterally(spec.from, spec.to))})

    repo.update(field.copy(numValues = newNumValue))
  }

  override def inputType = typeOf[ReplaceNumberSpec]
}

case class ReplaceNumberSpec(dataSetId: String, fieldName: String, batchSize: Int, from: String, to: String)