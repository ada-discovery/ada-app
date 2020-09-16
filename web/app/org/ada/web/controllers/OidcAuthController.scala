package org.ada.web.controllers

import javax.inject.Inject
import jp.t2v.lab.play2.auth.Login
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

      val importUserAfterLogin = configuration.getBoolean("oidc.importUserAfterLogin").getOrElse(true)

      // get a user info from the OIDC return data (profile)
      val oidcId = configuration.getString("oidc.returnAttributeIdName").flatMap( oidcIdName =>
        Option(profile.getAttribute(oidcIdName)).asInstanceOf[Option[String]]
      )

      val oidcUser = User(
        userId = userId,
        name = profile.getDisplayName,
        email = profile.getEmail,
        oidcId = oidcId
      )

      def successfulResult(extraMessage: String = "") = {
        Logger.info(s"Successful authentication for the user '${userId}' using the associated OIDC provider.$extraMessage")
        gotoLoginSucceeded(userId)
      }

      for {
        user <- userManager.findById(userId)

        result <- user.map { user =>
          // user exists locally... update (if needed)
          if (importUserAfterLogin) {
            userRepo.update(
              user.copy(
                name = oidcUser.name,
                email = oidcUser.email,
                oidcId = oidcId
              )
            ).flatMap(_ =>
              successfulResult()
            )
          } else
            successfulResult()

        }.getOrElse {
          // user doesn't exist locally... import/save if allowed
          if (importUserAfterLogin) {
            // import the new user locally...
            userRepo.save(oidcUser).flatMap(_ =>
              successfulResult(" new user imported")
            )
          } else {
            // show an error message
            val errorMessage = s"OIDC login cannot be fully completed. The user '${userId} doesn't exist locally."
            logger.warn(errorMessage)
            Future(Redirect(routes.AppController.index()).flashing("errors" -> errorMessage))
          }
        }
      } yield
        result
    }
  }
}