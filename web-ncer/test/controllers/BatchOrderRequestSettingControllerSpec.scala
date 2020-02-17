package controllers

import controllers.orderrequest.BatchOrderRequestSettingController
import org.ada.web.controllers.AuthController
import org.scalatest.AsyncFlatSpec
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc._
import play.api.libs.json._
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class BatchOrderRequestSettingControllerSpec extends PlaySpec with Results with GuiceOneAppPerSuite {

  val authController = app.injector.instanceOf[AuthController]
  val controller = app.injector.instanceOf[BatchOrderRequestSettingController]

  "BatchOrderRequestSettingController#create" should {
    "be valid" in {
      val authResult = Await.result(authController.loginAdmin.apply(FakeRequest()), 5 seconds)
      val header = authResult.header.headers.getOrElse("Set-Cookie", fail)
      val cookie = header.split(';')(0).split('=') match { case Array(name, value) => Cookie(name, value) }
      val request = FakeRequest(GET, "/requestSettings/new?dataSetId=12345").withCookies(cookie)
      val result = route(app, request).get
      status(result) mustBe OK
    }
  }


}
