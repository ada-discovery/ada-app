package services

import models.{BatchOrderRequest, BatchOrderRequestSetting, SampleDocumentation}
import models.BatchOrderRequest.batchRequestFormat
import net.codingwell.scalaguice.ScalaModule
import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.incal.core.dataaccess.AsyncCrudRepo
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import services.BatchOrderRequestRepoTypes.{BatchOrderRequestRepo, BatchOrderRequestSettingRepo, SampleDocumentationRepo}

class BatchOrderRequestModule extends ScalaModule {

  override def configure = {
    bind[BatchOrderRequestSettingRepo].toInstance(
      new MongoAsyncCrudRepo[BatchOrderRequestSetting, BSONObjectID]("batch_request_settings")
    )

    bind[BatchOrderRequestRepo].toInstance(
      new MongoAsyncCrudRepo[BatchOrderRequest, BSONObjectID]("batch_requests")
    )

    bind[SampleDocumentationRepo].toInstance(
      new MongoAsyncCrudRepo[SampleDocumentation, BSONObjectID]("sample_documentations")
    )
  }
}

object BatchOrderRequestRepoTypes {
  type BatchOrderRequestSettingRepo = AsyncCrudRepo[BatchOrderRequestSetting, BSONObjectID]
  type BatchOrderRequestRepo = AsyncCrudRepo[BatchOrderRequest, BSONObjectID]
  type SampleDocumentationRepo = AsyncCrudRepo[SampleDocumentation, BSONObjectID]
}
