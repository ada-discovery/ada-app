package dataaccess

import dataaccess.RepoTypes.RegressionResultRepo

trait RegressionResultRepoFactory {
  def apply(dataSetId: String): RegressionResultRepo
}
