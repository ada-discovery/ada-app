package scala.org.ada.server.controller


import org.ada.server.services.GuicePlayTestApp
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty}


class AuthControllerSpec extends PlaySpec {

  implicit val app: Application = GuicePlayTestApp()

  "The login entrypoint" should {
    "be redirect" in {
      checkRedirect("/login", "loginOIDC")
    }
  }

  def checkRedirect(url: String, target: String)(implicit app: Application) = {
    val resp = route(app, FakeRequest(GET, url)).get
    status(resp) mustEqual(Status.SEE_OTHER)
    redirectLocation(resp).contains(target)
  }







}
