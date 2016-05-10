package controllers

import javax.inject.Inject

import ldap.LdapUserRepo
import models.Page
import models.security.LdapUser
import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import play.twirl.api.Html

import views.html


/**
  *
  */
class LdapUserController @Inject() (userRepo: LdapUserRepo)
  extends ReadonlyController[LdapUser, String](userRepo){

  override def showView(
    id : String,
    item : LdapUser
  )(implicit msg: Messages, request: RequestHeader) : Html = {
    html.ldapviews.usershow(id, item)
  }

  override def listView(
    currentPage: Page[LdapUser]
  )(implicit msg: Messages, request: RequestHeader) : Html = html.ldapviews.userlist(currentPage)
}
