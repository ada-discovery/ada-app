package controllers

import javax.inject.Inject

import ldap.LdapUserRepo
import models.Page
import models.security.LdapUser
import play.api.i18n.Messages
import play.api.mvc.{Flash, RequestHeader, Request}

import views.html

class LdapUserController @Inject() (userRepo: LdapUserRepo) extends ReadonlyControllerImpl[LdapUser, String](userRepo) with AdminRestrictedReadonlyController[String] {

  override def showView(
    id : String,
    item : LdapUser
  )(implicit msg: Messages, request: Request[_]) =
    html.ldapviews.usershow(item)

  override def listView(
    currentPage: Page[LdapUser]
  )(implicit msg: Messages, request: Request[_]) =
    html.ldapviews.userlist(currentPage)
}