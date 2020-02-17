package controllers

import controllers.orderrequest.BatchOrderRequestController
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import services.BatchOrderRequestRepoTypes.BatchOrderRequestSettingRepo

class BatchOrderRequestControllerSpec extends PlaySpec with Results with GuiceOneAppPerSuite {

  val controller = app.injector.instanceOf[BatchOrderRequestController]

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