package org.ada.web.controllers

import com.nimbusds.jwt.JWTParser
import com.nimbusds.oauth2.sdk.token.{BearerAccessToken, RefreshToken}
import jp.t2v.lab.play2.auth.Login
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.models.User
import org.ada.server.services.UserManager
import org.ada.web.models.JwtTokenInfo
import org.ada.web.security.AdaAuthConfig
import org.pac4j.core.config.Config
import org.pac4j.core.profile._
import org.pac4j.play.scala._
import org.pac4j.play.store.PlaySessionStore
import play.api.cache.CacheApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import play.api.{Configuration, Logger}
import play.cache.NamedCache
import play.libs.concurrent.HttpExecutionContext

import javax.inject.Inject
import scala.concurrent.Future

class OidcAuthController @Inject() (
  val config: Config,                       // for PAC4J
  val playSessionStore: PlaySessionStore,   // for PAC4J
  override val ec: HttpExecutionContext,    // for PAC4J
  val userManager: UserManager,             // for Play2 Auth (AuthConfig)
  userRepo: UserRepo,
  configuration: Configuration,
  @NamedCache("jwt-user-cache") jwtUserCache: CacheApi
) extends Controller
    with Security[CommonProfile]            // PAC4J
    with Login                              // Play2 Auth
    with AdaAuthConfig {                    // Play2 Auth

  private val subAttribute = configuration.getString("oidc.returnAttributeIdName").getOrElse(
    new AdaException("Configuration issue: 'oidc.returnAttributeIdName' was not found in the configuration file.")
  )

  private val accessTokenAttribute = configuration.getString("oidc.accessTokenName").getOrElse(
    new AdaException("Configuration issue: 'oidc.accessTokenName' was not found in the configuration file.")
  )

  private val refreshTokenAttribute = configuration.getString("oidc.refreshTokenName").getOrElse(
    new AdaException("Configuration issue: 'oidc.refreshTokenName' was not found in the configuration file.")
  )


  def oidcLogin = Secure("OidcClient") { profiles =>
    Action.async { implicit request =>

      def successfulResult(user: User, extraMessage: String = "") = {
        Logger.info(s"Successful authentication for the user '${user.userId}', id '${user.oidcUserName}' using the associated OIDC provider.$extraMessage")
        gotoLoginSucceeded(user.userId)
      }

      /**
        * Manage existing user updating userId with oidcId,
        * username with old userId/username, name or email if necessary
        * @param existingUser user present in the database
        * @param oidcUser user from OpenID provider
        * @return Login information
        */
      def manageExistingUser(existingUser: User, oidcUser: User) = {
        if (existingUser.oidcUserName.isEmpty)
          updateUser(existingUser, oidcUser)
        else if (existingUser.oidcUserName.isDefined &&
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
          for {user <- userManager.findById(oidcUser.userId)
               result <- user.map(manageExistingUser(_, oidcUser))
                 .getOrElse(addNewUser(oidcUser))
               } yield result
      }

      def addNewUser(oidcUser: User) =
        userRepo.save(oidcUser).flatMap(_ =>
          successfulResult(oidcUser, " new user imported."))

      def updateUser(existingUser: User, oidcUser: User) = {
        userRepo.update(
          existingUser.copy(
            userId = oidcUser.userId,
            oidcUserName = oidcUser.oidcUserName,
            name = oidcUser.name,
            email = oidcUser.email
          )
        ).flatMap(_ =>
          successfulResult(oidcUser)
        )
      }

      val profile = profiles.head
      val userName = profile.getUsername
      val oidcIdOpt = Option(profile.getAttribute(s"$subAttribute", classOf[String]))
      val accessTokenOpt = Option(profile.getAttribute(s"$accessTokenAttribute", classOf[BearerAccessToken]))
      val refreshTokenOpt = Option(profile.getAttribute(s"$refreshTokenAttribute", classOf[RefreshToken]))

      if(oidcIdOpt.isEmpty || accessTokenOpt.isEmpty || refreshTokenOpt.isEmpty) {
        val errorMessage =
          s"OIDC login cannot be fully completed. The user '${userName} doesn't have one of these attributes ${subAttribute}, ${accessTokenAttribute}, ${refreshTokenAttribute} in Jwt token."
        logger.warn(errorMessage)
        Future(Redirect(routes.AppController.index()).flashing("errors" -> errorMessage))
      } else {
        val oidcUser = User(
          userId = oidcIdOpt.get,
          oidcUserName = Option(userName),
          name = profile.getDisplayName,
          email = profile.getEmail
        )

        jwtUserCache.set(oidcUser.userId, JwtTokenInfo(accessTokenOpt.get, refreshTokenOpt.get))

        for {
          user <- userManager.findById(userName)
          result <- user
            .map(manageExistingUser(_, oidcUser))
            .getOrElse(manageNewUser(oidcUser))
        } yield result
      }
    }
  }
}
