package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

//import ldap.RepoTypes.LdapUserRepo
import models.Page
import models.security.{LdapUser, CustomUser}
import models.workspace.UserGroup
import play.api.i18n.{MessagesApi, Messages}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, Controller, RequestHeader, Request}
import play.twirl.api.Html

import util.FilterSpec
import scala.collection.immutable.SortedSet
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import ldap.{LdapUserRepo, LdapConnector, LdapSettings}



/**
  * Controller for inspecting active ldap settings.
  *
  */
class LdapController @Inject() (
   ldapConnector: LdapConnector,
   ldapSettings: LdapSettings,
   ldapRepo: LdapUserRepo
 ) /*extends ReadonlyController(repo: LdapUserRepo)*/ extends Controller{


  def settings = Action{ implicit request =>
    Ok(views.html.ldapviews.viewSettings(ldapSettings))
  }


  def ldapList = Action { implicit request =>
    val entries: Traversable[String] = ldapConnector.getEntryList
    val content = "ldap entry list (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
    Ok(content)
  }

  /*def ldapGroups = Action { implicit request =>
    val entries: Traversable[UserGroup] = ldapserver.getUserGroups
    val content = "user groups (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
    Ok(content)
  }*/

  def ldapUsers = Action.async { implicit request =>
    //val entries: Future[Traversable[LdapUser]] = ldapRepo.find()


    ldapRepo.find().map{ entries: Traversable[LdapUser] =>
      val content = "users (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
      Ok(content)
    }

  }

}
