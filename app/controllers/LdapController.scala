package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import ldap.RepoTypes.LdapUserRepo
import models.Page
import models.security.CustomUser
import models.workspace.UserGroup
import play.api.Logger
import play.api.i18n.{MessagesApi, Messages}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, Controller, RequestHeader, Request}
import play.twirl.api.Html

import util.FilterSpec
import scala.collection.immutable.SortedSet
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import ldap.{LdapConnector, AdaLdapUserServer, LdapSettings}


/**
  * Controller for inspecting the current ldap settings.
  *
  */
class LdapController @Inject() (
   ldapserver: AdaLdapUserServer,
   ldapConnector: LdapConnector,
   ldapSettings: LdapSettings
 ) extends Controller{

  def settings = Action{ implicit request =>
    val values = ldapSettings.values
    Ok("")
  }



  def ldapList = Action { implicit request =>
    val entries: Traversable[String] = ldapserver.getEntryList
    val content = "ldap entry list (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
    Ok(content)
  }

  def ldapGroups = Action { implicit request =>
    val entries: Traversable[UserGroup] = ldapserver.getUserGroups
    val content = "user groups (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
    Ok(content)
  }

  def ldapUsers = Action { implicit request =>
    val entries: Traversable[CustomUser] = ldapserver.getUsers
    val content = "users (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
    Ok(content)
  }

}
