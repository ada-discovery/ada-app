package controllers

import org.ada.web.services.GuicePlayWebTestApp
import org.scalatestplus.play._
import play.api.Application
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._

class BatchOrderRequestControllerTest extends PlaySpec with Results  {

  implicit val app: Application = GuicePlayWebTestApp(excludeModules = Seq("org.ada.web.security.PacSecurityModule"))

  "The entry point BatchOrderRequestController#createNew" should {
    "should be return" in {
      val resp = route(app, FakeRequest(GET, "/requests/new?dataSet1=iris")).get
      status(resp) == 400
    }
  }
}