package services

import models.{BatchOrderRequest, BatchRequestSetting, SampleDocumentation}
import models.BatchOrderRequest.batchRequestFormat
import net.codingwell.scalaguice.ScalaModule
import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.incal.core.dataaccess.AsyncCrudRepo
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import services.BatchOrderRequestRepoTypes.{BatchOrderRequestRepo, RequestSettingRepo, SampleDocumentationRepo}

class BatchOrderRequestModule extends ScalaModule {

  override def configure = {
    bind[BatchOrderRequestRepo].toInstance(
      new MongoAsyncCrudRepo[BatchOrderRequest, BSONObjectID]("batch_requests")
    )

    bind[RequestSettingRepo].toInstance(
      new MongoAsyncCrudRepo[BatchRequestSetting, BSONObjectID]("batch_request_setting")
    )

    bind[SampleDocumentationRepo].toInstance(
      new MongoAsyncCrudRepo[SampleDocumentation, BSONObjectID]("sample_documentation")
    )
  }
}

object BatchOrderRequestRepoTypes {
  type BatchOrderRequestRepo = AsyncCrudRepo[BatchOrderRequest, BSONObjectID]
  type RequestSettingRepo = AsyncCrudRepo[BatchRequestSetting, BSONObjectID]
  type SampleDocumentationRepo = AsyncCrudRepo[SampleDocumentation, BSONObjectID]
}
