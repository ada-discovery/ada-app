package services

import models.{ApprovalCommittee, BatchRequest}
import models.BatchRequest.batchRequestFormat
import net.codingwell.scalaguice.ScalaModule
import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.incal.core.dataaccess.AsyncCrudRepo
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import services.BatchRequestRepoTypes.{ApprovalCommitteeRepo, BatchRequestRepo}

class BatchRequestModule extends ScalaModule {
  override def configure = {
    bind[BatchRequestRepo].toInstance(
      new MongoAsyncCrudRepo[BatchRequest, BSONObjectID]("batch_requests")
    )

    bind[ApprovalCommitteeRepo].toInstance(
      new MongoAsyncCrudRepo[ApprovalCommittee, BSONObjectID]("approval_committee")
    )
  }
}

object BatchRequestRepoTypes {
  type BatchRequestRepo = AsyncCrudRepo[BatchRequest, BSONObjectID]
  type ApprovalCommitteeRepo = AsyncCrudRepo[ApprovalCommittee, BSONObjectID]
}
