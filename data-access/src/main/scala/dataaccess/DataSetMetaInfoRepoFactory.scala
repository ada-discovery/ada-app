package dataaccess

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes.DataSetMetaInfoRepo
import dataaccess.ignite.DataSetMetaInfoCacheCrudRepoFactory
import reactivemongo.bson.BSONObjectID

@ImplementedBy(classOf[DataSetMetaInfoCacheCrudRepoFactory])
trait DataSetMetaInfoRepoFactory {
  def apply(dataSpaceId: BSONObjectID): DataSetMetaInfoRepo
}
