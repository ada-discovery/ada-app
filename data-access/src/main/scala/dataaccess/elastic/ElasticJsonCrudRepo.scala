package dataaccess.elastic

import com.google.inject.assistedinject.Assisted
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.mappings.TypedFieldDefinition
import com.sksamuel.elastic4s.source.JsonDocumentSource
import dataaccess.ignite.BinaryJsonUtil
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{FieldTypeId, FieldTypeSpec}
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONObjectIDFormat
import dataaccess.RepoTypes.JsonCrudRepo
import javax.inject.Inject

class ElasticJsonCrudRepo @Inject()(
    @Assisted collectionName : String,
    @Assisted fieldNamesAndTypes: Seq[(String, FieldTypeSpec)]
  ) extends ElasticAsyncCrudRepo[JsObject, BSONObjectID](collectionName, collectionName) with JsonCrudRepo {

  private implicit val jsonIdRenameFormat = JsonIdRenameFormat.apply
  private val fieldNamesAndTypeWithId = fieldNamesAndTypes ++ Seq((JsonIdRenameFormat.newIdName, FieldTypeSpec(FieldTypeId.Json)))

  override protected def serializeGetResult(response: RichGetResponse) = {
    Json.parse(response.sourceAsString) match {
      case JsNull => None
      case x: JsObject => jsonIdRenameFormat.reads(x).asOpt.map(_.asInstanceOf[JsObject])
      case _ => None
    }
  }

  override protected def serializeSearchResult(response: RichSearchResponse) = {
    response.hits.map { hit =>
//      val stringSource = hit.sourceAsString
//      val renamedStringSource = stringSource.replaceFirst("\"id\":", "\"_id\":")
//      JsObject(
//        hit.sourceAsMap.map { case (fieldName, value) =>
//          if (fieldName.equals("id"))
//            ("_id", Json.toJson(BSONObjectID.apply(value.asInstanceOf[util.HashMap[String, Any]].get("$oid").asInstanceOf[String])))
//          else
//            (fieldName, BinaryJsonUtil.toJson(value))
//        }.toSeq
//      )
      Json.parse(hit.sourceAsString) match {
        case x: JsObject => jsonIdRenameFormat.reads(x).asOpt.map(_.asInstanceOf[JsObject])
        case _ => None
      }
    }.toIterable.flatten
  }

  override protected def serializeSearchResult(result: Traversable[(String, Any)]) =
    JsObject(
      result.map { case (fieldName, value) =>
        if (fieldName.startsWith(JsonIdRenameFormat.newIdName))
          (JsonIdRenameFormat.originalIdName, Json.toJson(BSONObjectID.apply(value.asInstanceOf[String])))
        else
          (fieldName, BinaryJsonUtil.toJson(value))
      }.toSeq
    )

  override protected def createSaveDef(entity: JsObject, id: BSONObjectID) = {
    val stringSource = Json.stringify(jsonIdRenameFormat.writes(entity))
    index into indexAndType source stringSource id id
  }

  override def createUpdateDef(entity: JsObject, id: BSONObjectID) = {
    val stringSource = Json.stringify(jsonIdRenameFormat.writes(entity))
    ElasticDsl.update id id in indexAndType doc JsonDocumentSource(stringSource)
  }

  override protected def createIndex(c: ElasticClient = client) =
    c execute {
      create index collectionName replicas 0 mappings (
        collectionName as (
          fieldNamesAndTypeWithId.map { case (fieldName, fieldTypeSpec) =>
            toElasticFieldType(fieldName, fieldTypeSpec)
          }
        )
      ) indexSetting("max_result_window", unboundLimit) // indexSetting("mapping.coerce", true)
    }

  private def toElasticFieldType(fieldName: String, fieldTypeSpec: FieldTypeSpec): TypedFieldDefinition =
    fieldTypeSpec.fieldType match {
      case FieldTypeId.Integer => fieldName typed LongType
      case FieldTypeId.Double => new CoerceDoubleFieldDefinition(fieldName)
      case FieldTypeId.Boolean => fieldName typed BooleanType
      case FieldTypeId.Enum => fieldName typed IntegerType
      case FieldTypeId.String => fieldName typed StringType index NotAnalyzed
      case FieldTypeId.Date => fieldName typed LongType
      case FieldTypeId.Json => fieldName typed NestedType
      case FieldTypeId.Null => fieldName typed ShortType // doesnt matter which type since it's always null
    }
}