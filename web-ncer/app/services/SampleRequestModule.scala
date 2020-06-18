package services

import models.SampleRequestSetting
import net.codingwell.scalaguice.ScalaModule
import org.ada.server.dataaccess.mongo.MongoAsyncCrudRepo
import org.incal.core.dataaccess.AsyncCrudRepo
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import services.BatchOrderRequestRepoTypes.SampleRequestSettingRepo

class SampleRequestModule extends ScalaModule {
  override def configure() = {
    bind[SampleRequestSettingRepo].toInstance(
      new MongoAsyncCrudRepo[SampleRequestSetting, BSONObjectID]("sample_request_settings")
    )
  }
}

object BatchOrderRequestRepoTypes {
  type SampleRequestSettingRepo = AsyncCrudRepo[SampleRequestSetting, BSONObjectID]
}