package org.ada.web.controllers

import javax.inject.Inject
import jp.t2v.lab.play2.auth.Login
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.models.User
import org.ada.server.services.UserManager
import org.ada.web.security.AdaAuthConfig
import org.pac4j.core.config.Config
import org.pac4j.core.profile._
import org.pac4j.play.scala._
import org.pac4j.play.store.PlaySessionStore
import play.api.{Configuration, Logger}
import play.api.mvc._
import play.libs.concurrent.HttpExecutionContext
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.collection.JavaConversions._
import scala.concurrent.Future

class OidcAuthController @Inject() (
  val config: Config,                       // for PAC4J
  val playSessionStore: PlaySessionStore,   // for PAC4J
  override val ec: HttpExecutionContext,    // for PAC4J
  val userManager: UserManager,             // for Play2 Auth (AuthConfig)
  userRepo: UserRepo,
  configuration: Configuration
) extends Controller
    with Security[CommonProfile]            // PAC4J
    with Login                              // Play2 Auth
    with AdaAuthConfig {                    // Play2 Auth

  def oidcLogin = Secure("OidcClient") { profiles =>
    Action.async { implicit request =>
      val profile = profiles.head
      val userId = profile.getUsername
      println(profile.getAttributes().toMap.mkString("\n"))

      for {
        user <- userManager.findById(userId)

        result <- if (user.nonEmpty) {
          // user exists locally... all is good
          Logger.info(s"Successful authentication for the user '${userId}' using the OIDC provider.")
          gotoLoginSucceeded(userId)
        } else {
          // user doesn't exist locally...
          if (configuration.getBoolean("oidc.importUserAfterLogin").getOrElse(true)) {
            // import the new user locally.... // TODO: import an OIDC id
            val newUser = User(
              userId = userId,
              name = profile.getDisplayName,
              email = profile.getEmail
            )

            userRepo.save(newUser).flatMap { _ =>
              Logger.info(s"Successful authentication for the user '${userId}' using the OIDC provider (user imported).")
              gotoLoginSucceeded(userId)
            }
          } else {
            // show an error message
            val errorMessage = s"User '${userId} doesn't exist locally."
            logger.warn(errorMessage)
            Future(Redirect(routes.AppController.index()).flashing("errors" -> errorMessage))
          }
        }
      } yield
        result
    }
  }
}