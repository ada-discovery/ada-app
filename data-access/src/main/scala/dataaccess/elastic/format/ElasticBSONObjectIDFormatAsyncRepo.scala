package dataaccess.elastic.format

import com.sksamuel.elastic4s.ElasticClient
import dataaccess.elastic.{ElasticIdRenameFormat, ElasticSetting}
import org.incal.core.Identity
import play.api.libs.json.Format
import reactivemongo.bson.BSONObjectID

class ElasticBSONObjectIDFormatAsyncCrudRepo[E, ID](
  indexName: String,
  typeName: String,
  client: ElasticClient,
  setting: ElasticSetting)(
  implicit coreFormat: Format[E], override val manifest: Manifest[E], identity: Identity[E, ID]
) extends ElasticFormatAsyncCrudRepo[E, ID](
  indexName, typeName, client, setting
)(format = new ElasticIdRenameFormat(coreFormat)) {

  override protected def toDBValue(value: Any): Any =
    value match {
      case b: BSONObjectID => b.stringify
      case _ => super.toDBValue(value)
    }
}