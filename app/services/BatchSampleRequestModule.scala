package services

//import models.BatchSampleRequest
import models.{ApprovalCommittee, BatchSampleRequest}
import models.BatchSampleRequest.batchRequestFormat
import net.codingwell.scalaguice.ScalaModule
import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.incal.core.dataaccess.AsyncCrudRepo
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import services.BatchSampleRequestRepoTypes.{ApprovalCommitteeRepo, BatchSampleRequestRepo}

class BatchSampleRequestModule extends ScalaModule {
  override def configure = {
    bind[BatchSampleRequestRepo].toInstance(
      new MongoAsyncCrudRepo[BatchSampleRequest, BSONObjectID]("batch_sample_requests")
    )

    bind[ApprovalCommitteeRepo].toInstance(
      new MongoAsyncCrudRepo[ApprovalCommittee, BSONObjectID]("approval_committee")
    )
  }
}

object BatchSampleRequestRepoTypes {
  type BatchSampleRequestRepo = AsyncCrudRepo[BatchSampleRequest, BSONObjectID]
  type ApprovalCommitteeRepo = AsyncCrudRepo[ApprovalCommittee, BSONObjectID]
}
