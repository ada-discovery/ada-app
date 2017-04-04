package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller, Request}
import util.SecurityUtil._
import ldap.{LdapConnector, LdapSettings}

/**
  * Controller for inspecting active ldap settings.
  */
class LdapController @Inject() (
    ldapConnector: LdapConnector,
    ldapSettings: LdapSettings,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi
  ) extends Controller{

  protected implicit def toWebContext(implicit request: Request[_]): WebContext = {
    implicit val msg = messagesApi.preferred(request)
    WebContext()
  }

  def settings = restrictAdmin(deadbolt) {
    Action{ implicit request =>
      Ok(views.html.ldapviews.viewSettings(ldapSettings))
    }
  }

  def ldapList = restrictAdmin(deadbolt) {
    Action { implicit request =>
      val entries: Traversable[String] = ldapConnector.getEntryList
      val content = "ldap entry list (" + entries.size + "):\n" + entries.fold("")((s,a)=> a+"\n\n"+s)
      Ok(content)
    }
  }
}