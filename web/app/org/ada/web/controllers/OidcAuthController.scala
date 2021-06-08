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

  private val subAttribute = configuration.getString("oidc.returnAttributeIdName")

  def oidcLogin = Secure("OidcClient") { profiles =>
    Action.async { implicit request =>

      def successfulResult(user: User, extraMessage: String = "") = {
        Logger.info(s"Successful authentication for the user '${user.userId}', id '${user.oidcId}' using the associated OIDC provider.$extraMessage")
        gotoLoginSucceeded(user.oidcId.get.toString)
      }

      /**
        * Manage existing user updating oidc field and name or email if necessary
        * @param existingUser user present in the database
        * @param oidcUser user from OpenID provider
        * @return Login information
        */
      def manageExistingUser(existingUser: User, oidcUser: User) = {
        if (existingUser.oidcId.isEmpty)
          updateUser(existingUser, oidcUser)
        else if (existingUser.oidcId.isDefined &&
          (!existingUser.name.equalsIgnoreCase(oidcUser.name)
            || !existingUser.email.equalsIgnoreCase(oidcUser.email)))
          updateUser(existingUser, oidcUser)
        else
          successfulResult(existingUser)
      }

      /**
        * Manage new user.
        * Having multiple Openid providers is necessary to trigger a search
        * by UUID before adding the user in database.
        * @param oidcUser user from OpenID provider
        * @return Saving information
        */
       def manageNewUser(oidcUser: User) = {
        if(oidcUser.oidcId.isDefined) {
          for {user <- userManager.findByOidcId(oidcUser.oidcId)
               result <- user.map(manageExistingUser(_, oidcUser))
                 .getOrElse(addNewUser(oidcUser))
               } yield result
        } else {
          // show an error message
          val errorMessage = s"OIDC login cannot be fully completed. The user '${oidcUser.userId} doesn't have attribute ${subAttribute} in Jwt token."
          logger.warn(errorMessage)
          Future(Redirect(routes.AppController.index()).flashing("errors" -> errorMessage))
        }
      }

      def addNewUser(oidcUser: User) =
        userRepo.save(oidcUser).flatMap(_ =>
          successfulResult(oidcUser, " new user imported."))

      def updateUser(existingUser: User, oidcUser: User) = {
        userRepo.update(
          existingUser.copy(
            name = oidcUser.name,
            email = oidcUser.email,
            oidcId = oidcUser.oidcId
          )
        ).flatMap(_ =>
          successfulResult(oidcUser)
        )
      }

      val profile = profiles.head
      val userId = profile.getUsername

      // get a user info from the OIDC return data (profile)
      val oidcIdOpt = subAttribute.flatMap(oidcIdName =>
        Option(UUID.fromString(profile.getAttribute(oidcIdName).asInstanceOf[String])))

      val oidcUser = User(
        userId = userId,
        name = profile.getDisplayName,
        email = profile.getEmail,
        oidcId = oidcIdOpt
      )

      for {
        user <- userManager.findById(userId)
        result <- user
                  .map(manageExistingUser(_, oidcUser))
                  .getOrElse(manageNewUser(oidcUser))
      } yield result
    }
  }
}
