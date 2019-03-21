package dataaccess.elastic

import com.sksamuel.elastic4s.ElasticClient
import play.api.libs.json.Format
import reactivemongo.bson.BSONObjectID

class ElasticFormatAsyncReadonlyRepo[E, ID](
  indexName: String,
  typeName: String,
  identityName : String,
  client: ElasticClient,
  setting: ElasticSetting)(
  implicit coreFormat: Format[E], val manifest: Manifest[E]
) extends ElasticAsyncReadonlyRepo[E, ID](indexName, typeName, identityName, client, setting) with ElasticFormatSerializer[E] {

  override protected implicit val format: Format[E] = new ElasticIdRenameFormat(coreFormat)

  override protected def toDBValue(value: Any): Any =
    value match {
      case b: BSONObjectID => b.stringify
      case _ => super.toDBValue(value)
    }
}