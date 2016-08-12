package dataaccess.ignite

import javax.inject.{Inject, Provider}

import dataaccess.{Identity, AsyncCrudRepo}
import play.api.libs.json.Format
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import scala.reflect.runtime.universe._
import scala.reflect.ClassTag
import dataaccess.DataSetFormattersAndIds.{dataSetSettingFormat, DataSetSettingIdentity}

class CacheAsyncCrudRepoProvider[E: TypeTag, ID: ClassTag](
    val mongoCollectionName: String,
    val cacheName: Option[String] = None)(
    implicit val formatId: Format[ID], val formatE: Format[E], val identity: Identity[E, ID]
  ) extends Provider[AsyncCrudRepo[E, ID]] {

  @Inject var cacheRepoFactory: CacheAsyncCrudRepoFactory = _

  override def get(): AsyncCrudRepo[E, ID] =
    cacheRepoFactory.applyMongo[ID, E](mongoCollectionName, cacheName)
}