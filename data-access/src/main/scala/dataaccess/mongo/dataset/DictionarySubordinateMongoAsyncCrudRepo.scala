package dataaccess.mongo.dataset

import dataaccess.RepoTypes.DictionaryRootRepo
import org.incal.core.dataaccess.Criterion.Infix
import models.{DataSetFormattersAndIds, Dictionary}
import DataSetFormattersAndIds.{DictionaryIdentity, dictionaryFormat}
import dataaccess._
import dataaccess.mongo.SubordinateObjectMongoAsyncCrudRepo
import org.incal.core.Identity

import scala.concurrent.ExecutionContext.Implicits.global
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

protected[dataaccess] class DictionarySubordinateMongoAsyncCrudRepo[E: Format, ID: Format](
    listName: String,
    dataSetId: String,
    dictionaryRepo: DictionaryRootRepo)(
    implicit identity: Identity[E, ID]
  ) extends SubordinateObjectMongoAsyncCrudRepo[E, ID, Dictionary, BSONObjectID](listName, dictionaryRepo) {

  override protected def getDefaultRoot =
    Dictionary(None, dataSetId, Nil, Nil, Nil, Nil, Nil)

  override protected def getRootObject =
    dictionaryRepo.find(Seq("dataSetId" #== dataSetId)).map(_.headOption)
}
