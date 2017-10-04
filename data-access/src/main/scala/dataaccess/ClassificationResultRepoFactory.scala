package dataaccess

import dataaccess.RepoTypes.ClassificationResultRepo

trait ClassificationResultRepoFactory {
  def apply(dataSetId: String): ClassificationResultRepo
}
