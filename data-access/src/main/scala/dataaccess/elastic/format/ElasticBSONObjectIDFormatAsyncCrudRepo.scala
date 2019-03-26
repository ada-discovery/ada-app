package dataaccess.elastic.format

import com.sksamuel.elastic4s.ElasticClient
import org.incal.access_elastic.ElasticSetting
import org.incal.access_elastic.format.ElasticFormatAsyncRepo
import org.incal.core.Identity
import play.api.libs.json.Format
import reactivemongo.bson.BSONObjectID

final class ElasticBSONObjectIDFormatAsyncCrudRepo[E, ID](
  indexName: String,
  typeName: String,
  val client: ElasticClient,
  setting: ElasticSetting)(
  implicit coreFormat: Format[E], manifest: Manifest[E], identity: Identity[E, ID]
) extends ElasticFormatAsyncRepo[E, ID](
  indexName, typeName, setting
)(format = new ElasticIdRenameFormat(coreFormat), manifest, identity) {

  override protected def toDBValue(value: Any): Any =
    value match {
      case b: BSONObjectID => b.stringify
      case _ => super.toDBValue(value)
    }

  override protected def toDBFieldName(fieldName: String) =
    ElasticIdRenameUtil.rename(fieldName, true)

  override protected def unrename(fieldName: String) =
    ElasticIdRenameUtil.unrename(fieldName)
}