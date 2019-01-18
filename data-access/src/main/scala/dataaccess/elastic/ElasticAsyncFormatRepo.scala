package dataaccess.elastic

import com.sksamuel.elastic4s.{ElasticClient, ElasticDsl, IndexDefinition}
import org.incal.core.Identity
import play.api.libs.json.Format

class ElasticFormatAsyncReadonlyRepo[E, ID](
    indexName: String,
    typeName: String,
    identityName : String,
    client: ElasticClient,
    setting: ElasticSetting)(
    implicit coreFormat: Format[E], val manifest: Manifest[E]
  ) extends ElasticAsyncReadonlyRepo[E, ID](indexName, typeName, identityName, client, setting) with ElasticFormatSerializer[E] {

  override protected implicit val format: Format[E] = new ElasticIdRenameFormat(coreFormat)
}

class ElasticFormatAsyncRepo[E, ID](
    indexName: String,
    typeName: String,
    client: ElasticClient,
    setting: ElasticSetting)(
    implicit coreFormat: Format[E], val manifest: Manifest[E], identity: Identity[E, ID]
  ) extends ElasticAsyncRepo[E, ID](indexName, typeName, client, setting) with ElasticFormatSerializer[E] {

  override protected implicit val format: Format[E] = new ElasticIdRenameFormat(coreFormat)

  // we need to explicitly create an indexable implicit since coreFormat and format can be both ambiguously substituted
  private implicit val indexable = jsonWritesToIndexable[E](format)

  override protected def createSaveDef(entity: E, id: ID): IndexDefinition =
    index into indexAndType source entity id id
}

class ElasticFormatAsyncCrudRepo[E, ID](
    indexName: String,
    typeName: String,
    client: ElasticClient,
    setting: ElasticSetting)(
    implicit coreFormat: Format[E], val manifest: Manifest[E], identity: Identity[E, ID]
  ) extends ElasticAsyncCrudRepo[E, ID](indexName, typeName, client, setting) with ElasticFormatSerializer[E] {

  override protected implicit val format: Format[E] = new ElasticIdRenameFormat(coreFormat)
  // we need to explicitly create an indexable implicit since coreFormat and format can be both ambiguously substituted
  private implicit val indexable = jsonWritesToIndexable[E](format)

  override protected def createSaveDef(entity: E, id: ID) =
    index into indexAndType source entity id id

  override def createUpdateDef(entity: E, id: ID) =
    ElasticDsl.update id id in indexAndType source entity
}