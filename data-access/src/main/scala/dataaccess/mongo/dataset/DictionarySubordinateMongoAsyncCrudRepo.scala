package dataaccess.mongo.dataset

import dataaccess.Criterion.CriterionInfix
import dataaccess.DataSetFormattersAndIds._
import dataaccess._
import dataaccess.mongo.SubordinateObjectMongoAsyncCrudRepo
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json._
import play.modules.reactivemongo.json.BSONObjectIDFormat
import reactivemongo.bson.BSONObjectID
import dataaccess.RepoTypes._

protected[dataaccess] class DictionarySubordinateMongoAsyncCrudRepo[E: Format, ID: Format](
    listName: String,
    dataSetId: String,
    dictionaryRepo: DictionaryRootRepo)(
    implicit identity: Identity[E, ID]
  ) extends SubordinateObjectMongoAsyncCrudRepo[E, ID, Dictionary, BSONObjectID](listName, dictionaryRepo) {

    override protected def getDefaultRoot =
      Dictionary(None, dataSetId, Seq[Field](), Seq[Category]())

    override protected def getRootObject =
      dictionaryRepo.find(Seq("dataSetId" #== dataSetId)).map(_.headOption)
}
