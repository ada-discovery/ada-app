package persistence.dataset

import persistence.RepoTypes._

trait DataSetRepo extends JsObjectCrudRepo {

  def dataSetId: String
}
