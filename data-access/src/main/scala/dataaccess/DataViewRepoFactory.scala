package dataaccess

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes.FilterRepo
import dataaccess.ignite.FilterCacheCrudRepoFactory

@ImplementedBy(classOf[FilterCacheCrudRepoFactory])
trait DataViewRepoFactory {
  def apply(dataSetId: String): FilterRepo
}