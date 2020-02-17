package controllers

import controllers.orderrequest.BatchOrderRequestSettingController
import org.ada.web.controllers.AuthController
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._


class BatchOrderRequestSettingControllerSpec extends PlaySpec with Results with GuiceOneAppPerSuite {

  val authController = app.injector.instanceOf[AuthController]
  val controller = app.injector.instanceOf[BatchOrderRequestSettingController]

  "BatchOrderRequestSettingController#create" should {
    "be valid" in {
      val cookie = TestUtils.getLoginCookie(app)
      val request = FakeRequest(GET, "/requestSettings/new?dataSetId=12345").withCookies(cookie)
      val result = route(app, request).get
      status(result) mustBe OK
    }
  }
}
