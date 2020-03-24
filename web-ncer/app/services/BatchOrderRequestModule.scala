package services

import models.BatchOrderRequestSetting
import net.codingwell.scalaguice.ScalaModule
import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.incal.core.dataaccess.AsyncCrudRepo
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import services.BatchOrderRequestRepoTypes.BatchOrderRequestSettingRepo

class BatchOrderRequestModule extends ScalaModule {

  override def configure() = {
    bind[BatchOrderRequestSettingRepo].toInstance(
      new MongoAsyncCrudRepo[BatchOrderRequestSetting, BSONObjectID]("batch_request_settings")
    )
  }
}

object BatchOrderRequestRepoTypes {
  type BatchOrderRequestSettingRepo = AsyncCrudRepo[BatchOrderRequestSetting, BSONObjectID]
}
