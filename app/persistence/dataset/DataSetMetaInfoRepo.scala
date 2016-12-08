package persistence.dataset

import javax.inject.Inject

import com.google.inject.assistedinject.Assisted
import dataaccess.Criterion
import dataaccess.mongo.SubordinateObjectMongoAsyncCrudRepo
import models.DataSetFormattersAndIds._
import models._
import Criterion.Infix
import persistence.RepoTypes._
import persistence._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

trait DataSetMetaInfoRepoFactory {
  def apply(dataSpaceId: BSONObjectID): DataSetMetaInfoRepo
}

protected[persistence] class DataSetMetaInfoSubordinateMongoAsyncCrudRepo @Inject()(
    @Assisted dataSpaceId: BSONObjectID,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends SubordinateObjectMongoAsyncCrudRepo[DataSetMetaInfo, BSONObjectID, DataSpaceMetaInfo, BSONObjectID]("dataSetMetaInfos", dataSpaceMetaInfoRepo) {

  override protected def getDefaultRoot =
    DataSpaceMetaInfo(Some(dataSpaceId), "", 0, new java.util.Date(), Seq[DataSetMetaInfo]())

  override protected def getRootObject =
    dataSpaceMetaInfoRepo.find(Seq(DataSpaceMetaInfoIdentity.name #== dataSpaceId)).map(_.headOption)

  override def save(entity: DataSetMetaInfo): Future[BSONObjectID] = {
    val identity = DataSetMetaInfoIdentity
    val initializedId = identity.of(entity).getOrElse(BSONObjectID.generate)
    super.save(identity.set(entity, initializedId))
  }
}
