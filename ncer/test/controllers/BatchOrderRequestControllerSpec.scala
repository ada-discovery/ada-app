package controllers

import controllers.orderrequest.BatchOrderRequestController
import org.ada.server.services.GuicePlayTestApp
import org.scalatestplus.play._
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import services.BatchOrderRequestRepoTypes.BatchOrderRequestSettingRepo

class BatchOrderRequestControllerSpec extends PlaySpec with Results {

  private val injector = GuicePlayTestApp(Seq("play.modules.reactivemongo.ReactiveMongoModule")).injector
  private val controller = injector.instanceOf[BatchOrderRequestController]

  "BatchOrderRequestController#createNew" should {
    "should be valid" in {
      val dataset = "iris"
      val request = FakeRequest()
      val result = controller.createNew(dataset).apply(request)
      val bodyText = contentAsString(result)
      bodyText mustBe "ok"
    }
  }
}