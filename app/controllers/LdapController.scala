package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions}
import controllers.core.WebContext
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Controller, Request}
import util.SecurityUtil._
import ldap.{LdapConnector, LdapSettings}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
  * Controller for inspecting active ldap settings.
  */
class LdapController @Inject() (
    ldapConnector: LdapConnector,
    ldapSettings: LdapSettings,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi,
    webJarAssets: WebJarAssets
  ) extends Controller{

  //  private implicit def webContext(implicit request: Request[_]) = WebContext(messagesApi)
  private implicit def webContext(implicit request: AuthenticatedRequest[_]) = WebContext(messagesApi, webJarAssets)

  def settings = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future (
      Ok(views.html.ldapviews.viewSettings(ldapSettings))
    )
  }

  def ldapList = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future {
      val entries: Traversable[String] = ldapConnector.getEntryList
      val content = "ldap entry list (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
      Ok(content)
    }
  }
}