package persistence.dataset

import models.DataSetFormattersAndIds._
import models.{Identity, Dictionary, Field, Category}
import models.Criterion.CriterionInfix
import persistence._
import persistence.RepoTypes.DictionaryRootRepo
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.modules.reactivemongo.json.BSONObjectIDFormat
import reactivemongo.bson.BSONObjectID

protected[persistence] class DictionarySubordinateMongoAsyncCrudRepo[E: Format, ID: Format](
    listName: String,
    dataSetId: String,
    dictionaryRepo: DictionaryRootRepo)(
    implicit identity: Identity[E, ID]
  ) extends SubordinateObjectMongoAsyncCrudRepo[E, ID, Dictionary, BSONObjectID](listName, dictionaryRepo) {

    override protected def getDefaultRoot =
      Dictionary(None, dataSetId, Seq[Field](), Seq[Category]())

    override protected def getRootObject =
      dictionaryRepo.find(Seq("dataSetId" #= dataSetId)).map(_.headOption)
}
