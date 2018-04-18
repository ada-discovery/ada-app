package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions}
import controllers.core.WebContext
import ldap.LdapUserService
import views.html.ldapviews._
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc._
import util.SecurityUtil._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LdapUserController @Inject() (
    ldapUserService: LdapUserService,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi,
    webJarAssets: WebJarAssets
  ) extends Controller {

  private implicit def webContext(implicit request: AuthenticatedRequest[_]) = WebContext(messagesApi, webJarAssets)

  def listAll = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future {
      implicit val msg = messagesApi.preferred(request)

      val all = ldapUserService.getAll
      Ok(userlist(all))
    }
  }

  def get(id: String) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request => Future {
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