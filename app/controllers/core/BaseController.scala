package controllers.core

import javax.inject.Inject

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions}
import controllers.WebJarAssets
import play.api.Configuration
import play.api.i18n.MessagesApi
import play.api.mvc.{Controller, Request}

class BaseController extends Controller {

  @Inject protected var messagesApi: MessagesApi = _
  @Inject protected var deadbolt: DeadboltActions = _
  @Inject protected var webJarAssets: WebJarAssets = _
  @Inject protected var configuration: Configuration = _

//  protected implicit def webContext(implicit request: Request[_]) = {
//    implicit val authenticatedRequest = new AuthenticatedRequest(request, None)
//    WebContext(messagesApi, webJarAssets, configuration)
//  }

  protected implicit def webContext(implicit request: AuthenticatedRequest[_]) =
    WebContext(messagesApi, webJarAssets, configuration)(request)
}