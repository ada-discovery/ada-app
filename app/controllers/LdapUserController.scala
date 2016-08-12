package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import ldap.LdapUserService
import models.Page
import play.api.i18n.{MessagesApi, Messages}
import play.api.mvc._
import util.SecurityUtil._

import views.html

import scala.concurrent.Future

class LdapUserController @Inject() (
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi,
    ldapUserService: LdapUserService
  ) extends Controller {

  def listAll = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)

      val all = ldapUserService.getAll
      Ok(html.ldapviews.userlist(Page(all, 0, 0, all.size, "", Nil)))
    }
  }

  def get(id: String) = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)

      val userOption = ldapUserService.getAll.find{entry => (entry.getDN == id)}
      userOption.fold(
        BadRequest(s"LDAP user with id '$id' not found.")
      ) {
        user => Ok(html.ldapviews.usershow(user))
      }
    }
  }
}