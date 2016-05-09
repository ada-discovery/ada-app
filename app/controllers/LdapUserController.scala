package controllers

import javax.inject.Inject

import ldap.LdapUserRepo
import models.Page
import models.security.LdapUser
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import play.twirl.api.Html
import play.api.data.Forms._
import play.api.data._


import views.html


/**
  *
  */
class LdapUserController @Inject() (userRepo: LdapUserRepo)
  extends ReadonlyController[LdapUser, String](userRepo){

  protected val form = Form(
    mapping(
      "_id" -> text,
      "name" -> text,
      "email" -> email,
      "affiliation" -> text,
      "permissions" -> seq(text)
    )(LdapUser.apply)(LdapUser.unapply))

  override def showView(
    id : String,
    item : LdapUser
  )(implicit msg: Messages, request: RequestHeader) : Html = html.ldapviews.usershow(id, form)

  override def listView(
    currentPage: Page[LdapUser]
  )(implicit msg: Messages, request: RequestHeader) : Html = html.ldapviews.userlist(currentPage)
}
