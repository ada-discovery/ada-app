package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import controllers.core.WebContext
import ldap.LdapUserService
import views.html.ldapviews._
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc._
import util.SecurityUtil._

class LdapUserController @Inject() (
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi,
    ldapUserService: LdapUserService
  ) extends Controller {

  private implicit def webContext(implicit request: Request[_]) = WebContext(messagesApi)

  def listAll = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)

      val all = ldapUserService.getAll
      Ok(userlist(all))
    }
  }

  def get(id: String) = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)

      val userOption = ldapUserService.getAll.find{entry => (entry.uid == id)}
      userOption.fold(
        BadRequest(s"LDAP user with id '$id' not found.")
      ) {
        user => Ok(usershow(user))
      }
    }
  }
}