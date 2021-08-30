package controllers

import org.ada.web.controllers.AuthController
import play.api.Application
import play.api.mvc.Cookie
import play.api.test.FakeRequest

import scala.concurrent.Await
import scala.concurrent.duration._

object TestUtils {
  def getLoginCookie(app: Application) = {
    val authController = app.injector.instanceOf[AuthController]
    val authResult = Await.result(authController.loginAdmin.apply(FakeRequest()), 10 seconds)
    val header = authResult.header.headers.getOrElse("Set-Cookie", throw new RuntimeException("login failed"))
    header.split(';')(0).split('=') match { case Array(name, value) => Cookie(name, value) }
  }
}
