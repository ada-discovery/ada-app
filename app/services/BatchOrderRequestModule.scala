package services

import models.{ApprovalCommittee, BatchOrderRequest}
import models.BatchOrderRequest.batchRequestFormat
import net.codingwell.scalaguice.ScalaModule
import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.incal.core.dataaccess.AsyncCrudRepo
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}

class BatchOrderRequestModule extends ScalaModule {

  override def configure = {
    bind[BatchOrderRequestRepo].toInstance(
      new MongoAsyncCrudRepo[BatchOrderRequest, BSONObjectID]("batch_requests")
    )

    bind[ApprovalCommitteeRepo].toInstance(
      new MongoAsyncCrudRepo[ApprovalCommittee, BSONObjectID]("approval_committees")
    )
  }
}

object BatchOrderRequestRepoTypes {
  type BatchOrderRequestRepo = AsyncCrudRepo[BatchOrderRequest, BSONObjectID]
  type ApprovalCommitteeRepo = AsyncCrudRepo[ApprovalCommittee, BSONObjectID]
}
