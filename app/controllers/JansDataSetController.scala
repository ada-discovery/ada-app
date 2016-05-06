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
  ldapserver: AdaLdapUserServer,
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


  def ldapAuth = deadbolt.SubjectPresent(handlerCache.ldapHandler){
    Action { implicit request =>
      Ok("authorized")
    }
  }

  def ldapmail(mail: String) = Action.async { implicit request =>
    val usropfuture: Future[Option[CustomUser]] = ldapserver.findByEmail(mail)
    usropfuture.map{userop =>
      userop match{
        case Some(usr) => Ok("found: " + usr)
        case None => Ok("not found")
      }
    }
  }

  def ldapid(id: String) = Action.async { implicit request =>
    val usropfuture: Future[Option[CustomUser]] = ldapserver.findById(id)
    usropfuture.map{userop =>
      userop match{
        case Some(usr) => Ok("found: " + usr)
        case None => Ok("not found")
      }
    }
  }

  def listLDAP = Action { implicit request =>
    val entries: Traversable[String] = ldapserver.getEntryList
    val content = "ldap entry list (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
    Ok(content)
  }

  def ldapgroups = Action { implicit request =>
    val entries = ldapserver.getUserGroups
    val content = "user groups (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
    Ok(content)
  }

  def ldapusers = Action { implicit request =>
    val entries = ldapserver.getUsers
    val content = "users (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
    Ok(content)
  }
}