package dataaccess.elastic

import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl, IndexDefinition}
import org.incal.core.Identity
import play.api.libs.json.Format
import reactivemongo.bson.BSONObjectID

class ElasticFormatAsyncRepo[E, ID](
  indexName: String,
  typeName: String,
  client: ElasticClient,
  setting: ElasticSetting)(
  implicit coreFormat: Format[E], val manifest: Manifest[E], identity: Identity[E, ID]
) extends ElasticAsyncRepo[E, ID](indexName, typeName, client, setting) with ElasticFormatSerializer[E] {

  override protected implicit val format: Format[E] = new ElasticIdRenameFormat(coreFormat)

  // we need to explicitly create an indexable implicit since coreFormat and format can be both ambiguously substituted
  private implicit val indexable = toIndexable[E](format)

  override protected def createSaveDef(entity: E, id: ID): IndexDefinition =
    index into indexAndType source entity id id

  override protected def toDBValue(value: Any): Any =
    value match {
      case b: BSONObjectID => b.stringify
      case _ => super.toDBValue(value)
    }
}