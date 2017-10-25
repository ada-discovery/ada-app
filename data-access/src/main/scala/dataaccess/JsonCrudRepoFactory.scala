package dataaccess

import dataaccess.RepoTypes._
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.FieldTypeSpec
import play.api.libs.json.JsObject
import reactivemongo.play.json.BSONFormats._
import reactivemongo.bson.BSONObjectID
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

trait JsonCrudRepoFactory {
  def apply(
    collectionName: String,
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)]
  ): JsonCrudRepo
}

trait MongoJsonCrudRepoFactory {
  def apply(
    collectionName: String,
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],
    createIndexForProjectionAutomatically: Boolean
  ): JsonCrudRepo
}

object JsonCrudRepoExtra {

  private val idName = JsObjectIdentity.name

  implicit class InfixOps(val dataSetRepo: JsonCrudRepo) extends AnyVal {

    import dataaccess.Criterion.Infix

    def allIds: Future[Traversable[BSONObjectID]] =
      dataSetRepo.find(
        projection = Seq(idName)
      ).map { jsons =>
        val ids  = jsons.map(json => (json \ idName).as[BSONObjectID])
        ids.toSeq.sortBy(_.stringify)
      }

    def findByIds(
      firstId: BSONObjectID,
      batchSize: Int,
      projection: Traversable[String]
    ): Future[Traversable[JsObject]] =
      dataSetRepo.find(
        criteria = Seq(idName #>= firstId),
        limit = Some(batchSize),
        sort = Seq(AscSort(idName)),
        projection = projection
      )
  }
}