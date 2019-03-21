package dataaccess.elastic.format

import com.sksamuel.elastic4s.ElasticClient
import dataaccess.elastic.ElasticSetting
import play.api.libs.json.Format
import reactivemongo.bson.BSONObjectID

class ElasticBSONObjectIDFormatAsyncReadonlyRepo[E, ID](
  indexName: String,
  typeName: String,
  identityName : String,
  client: ElasticClient,
  setting: ElasticSetting)(
  implicit coreFormat: Format[E], manifest: Manifest[E]
) extends ElasticFormatAsyncReadonlyRepo[E, ID](
  indexName, typeName, identityName, client, setting
)(format = new ElasticIdRenameFormat(coreFormat), manifest) {

  override protected def toDBValue(value: Any): Any =
    value match {
      case b: BSONObjectID => b.stringify
      case _ => super.toDBValue(value)
    }
}
