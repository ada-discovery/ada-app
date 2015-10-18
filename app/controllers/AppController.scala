package controllers

import play.api.mvc.{Flash, Action, Controller}

class AppController extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.home())
  }
}
