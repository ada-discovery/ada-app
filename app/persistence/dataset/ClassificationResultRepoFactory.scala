package persistence.dataset

import persistence.RepoTypes.ClassificationResultRepo

trait ClassificationResultRepoFactory {
  def apply(dataSetId: String): ClassificationResultRepo
}
