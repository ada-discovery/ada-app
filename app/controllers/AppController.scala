package controllers

import javax.inject.Inject

import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller}

class AppController extends Controller {

  @Inject var messagesApi: MessagesApi = _

  def index = Action { implicit request =>
    Ok(views.html.layout.home())
  }
}