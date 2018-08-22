package controllers

import javax.inject.Inject

import org.incal.play.security.SecurityUtil._
import ldap.{LdapConnector, LdapSettings}
import org.incal.play.controllers.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Controller for inspecting active ldap settings.
  */
class LdapController @Inject() (
    ldapConnector: LdapConnector,
    ldapSettings: LdapSettings
  ) extends BaseController {

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