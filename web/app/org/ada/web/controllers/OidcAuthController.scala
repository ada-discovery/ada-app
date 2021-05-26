package org.ada.web.controllers

import jp.t2v.lab.play2.auth.Login
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.models.User
import org.ada.server.services.UserManager
import org.ada.web.security.AdaAuthConfig
import org.pac4j.core.config.Config
import org.pac4j.core.profile._
import org.pac4j.play.scala._
import org.pac4j.play.store.PlaySessionStore
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import play.api.{Configuration, Logger}
import play.libs.concurrent.HttpExecutionContext

import java.util.UUID
import javax.inject.Inject
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

      // get a user info from the OIDC return data (profile)
      val subAttribute = configuration.getString("oidc.returnAttributeIdName")
      val oidcIdOpt = subAttribute.flatMap( oidcIdName =>
        Option(UUID.fromString(profile.getAttribute(oidcIdName).asInstanceOf[String])))

      oidcIdOpt match {
        case Some(oidcId) =>

          def successfulResult(extraMessage: String = "") = {
            Logger.info(s"Successful authentication for the user '${userId}', id '${oidcId}' using the associated OIDC provider.$extraMessage")
            gotoLoginSucceeded(oidcId.toString)
          }

          val oidcUser = User(
            userId = userId,
            name = profile.getDisplayName,
            email = profile.getEmail,
            oidcId = oidcIdOpt
          )

          for {
            user <- userManager.findByOidcId(oidcUser.oidcId)
            result <- user.map { user =>
              // user exists locally... update (if needed)
              if (!user.name.equalsIgnoreCase(oidcUser.name) || !user.email.equalsIgnoreCase(oidcUser.email)) {
                logger.warn("===========> Update")
                logger.warn("===========> OidcUser name" + oidcUser.name + " email " + oidcUser.email + "Case class " + oidcUser)
                logger.warn("===========> User " + user)
                userRepo.update(
                  user.copy(
                    name = oidcUser.name,
                    email = oidcUser.email,
                    oidcId = oidcUser.oidcId
                  )
                ).flatMap(_ =>
                  successfulResult()
                )
              } else
                successfulResult()

            }.getOrElse{
              // user doesn't exist locally
              logger.warn("==============> Insert")
                userRepo.save(oidcUser).flatMap(_ =>
                  successfulResult(" new user imported.")
                )
            }
          } yield
            result
        case None =>
          // show an error message
          val errorMessage = s"OIDC login cannot be fully completed. The user '${userId} doesn't have attribute $subAttribute in Jwt token."
          logger.warn(errorMessage)
          Future(Redirect(routes.AppController.index()).flashing("errors" -> errorMessage))
      }

    }
  }
}