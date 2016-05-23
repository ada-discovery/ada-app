package controllers

import javax.inject.Inject
import play.api.mvc.{Action, Controller}

import models.security.CustomUser

import be.objectify.deadbolt.scala.DeadboltActions

import ldap._
import security.CustomHandlerCache

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._



/**
 * Class for testing and debugging
 */
class JansDataSetController @Inject()(
  handlerCache: CustomHandlerCache,
  deadbolt: DeadboltActions) extends Controller{

  // debug: show session
  def showSession = Action { implicit request =>
    Ok(request.session.toString)
  }
  // debug: clear session
  def clearSession = Action { implicit request =>
    Ok("sessions cleared").withNewSession
  }
}