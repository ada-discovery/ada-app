package persistence.dataset

import persistence.RepoTypes.RegressionResultRepo

trait RegressionResultRepoFactory {
  def apply(dataSetId: String): RegressionResultRepo
}
