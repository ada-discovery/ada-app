package runnables.core

import dataaccess.RepoTypes.JsonCrudRepo
import field.{FieldType, FieldTypeHelper}
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{AdaException, Field, FieldTypeId}
import play.api.libs.json.{JsNull, JsString, JsValue, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import runnables.DsaInputFutureRunnable
import org.incal.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class DropExtraMiliSecsPoints extends DsaInputFutureRunnable[DropExtraMiliSecsPointsSpec] {

  private val idName = JsObjectIdentity.name
  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(spec: DropExtraMiliSecsPointsSpec) = {
    val dsa_ = dsa(spec.dataSetId)

    for {
      // field
      fieldOption <- dsa_.fieldRepo.get(spec.fieldName)
      field = fieldOption.getOrElse(throw new AdaException(s"Field ${spec.fieldName} not found."))
      fieldType = ftf(field.fieldTypeSpec)

      // replace for String or Enum
      _ <- field.fieldTypeSpec.fieldType match {
        case FieldTypeId.String => replaceForString(dsa_.dataSetRepo, fieldType.asValueOf[String], spec)
        case _ => throw new AdaException(s"DropExtraMiliSecsPoints is possible only for String type but got ${field.fieldTypeSpec}.")
      }
    } yield
      ()
  }

  private def replaceForString(
    repo: JsonCrudRepo,
    fieldType: FieldType[String],
    spec: DropExtraMiliSecsPointsSpec
  ) = {
    for {
      // jsons
      idJsons <- repo.find(projection = Seq(idName, spec.fieldName))

      // get the records as String and replace
      idReplacedStringValues = idJsons.map { json =>
        val id = (json \ JsObjectIdentity.name).as[BSONObjectID]
        val replacedStringValue = fieldType.jsonToValue(json \ spec.fieldName).map { originalValue =>
          val dotIndex = originalValue.indexOf('.')
          if (dotIndex > 0)
            originalValue.substring(0, Math.min(originalValue.length, dotIndex + 4))
          else
            originalValue
        }
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

  override def inputType = typeOf[DropExtraMiliSecsPointsSpec]
}

case class DropExtraMiliSecsPointsSpec(dataSetId: String, fieldName: String, batchSize: Int)