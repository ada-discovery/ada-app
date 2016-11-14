package dataaccess

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes.DataViewRepo
import dataaccess.ignite.DataViewCacheCrudRepoFactory

@ImplementedBy(classOf[DataViewCacheCrudRepoFactory])
trait DataViewRepoFactory {
  def apply(dataSetId: String): DataViewRepo
}