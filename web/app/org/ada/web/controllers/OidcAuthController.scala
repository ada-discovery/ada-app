package org.ada.web.controllers

import com.nimbusds.jwt.JWTParser
import com.nimbusds.oauth2.sdk.token.{BearerAccessToken, RefreshToken}
import jp.t2v.lab.play2.auth.Login
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, UserRepo}
import org.ada.server.models.User
import org.ada.server.services.UserManager
import org.ada.web.controllers.UserDataSetPermissions.viewOnly
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
import org.incal.core.dataaccess.Criterion._

import javax.inject.Inject
import scala.concurrent.Future
import scala.util.matching.Regex

class OidcAuthController @Inject() (
  val config: Config,                       // for PAC4J
  val playSessionStore: PlaySessionStore,   // for PAC4J
  override val ec: HttpExecutionContext,    // for PAC4J
  val userManager: UserManager,             // for Play2 Auth (AuthConfig)
  userRepo: UserRepo,
  dataSettingRepo: DataSetSettingRepo,
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

  private val rolesAttribute = configuration.getString("oidc.rolesAttributeName").getOrElse(
    new AdaException("Configuration issue: 'oidc.rolesAttributeName' was not found in the configuration file.")
  )

  private val dataSetGlobalIdPrefix = configuration.getString("oidc.dataSetGlobalIdPrefix").getOrElse(
    new AdaException("Configuration issue: 'oidc.dataSetGlobalIdPrefix' was not found in the configuration file.")
  )

  private val dataSetGlobalIdRegexStr = configuration.getString("oidc.dataSetGlobalIdRegex").getOrElse(
    new AdaException("Configuration issue: 'oidc.dataSetGlobalIdRegex' was not found in the configuration file.")
  )

  private val dataSetGlobalIdRegex = new Regex(s"$dataSetGlobalIdRegexStr")

  def oidcLogin = Secure("OidcClient") { profiles =>
    Action.async { implicit request =>

      def successfulResult(user: User, extraMessage: String = "") = {
        Logger.info(s"Successful authentication for the user '${user.userId}', id '${user.oidcUserName}' using the associated OIDC provider.$extraMessage")
        gotoLoginSucceeded(user.userId)
      }

      /**
        * Parse JWT access token to get dataSetGlobalIds
        * @param accessToken
        * @return Global ids sequence (DataCatalog)
        */
      def parseDataSetGlobalIds(accessToken: BearerAccessToken): Seq[Option[String]] = {
        val jwtAccessTokenClaim = JWTParser.parse(accessToken.getValue).getJWTClaimsSet
        val roles = jwtAccessTokenClaim.getStringArrayClaim(s"$rolesAttribute").toSeq
        roles.filter(_.startsWith(s"$dataSetGlobalIdPrefix"))
          .map(access => dataSetGlobalIdRegex.findFirstMatchIn(access) match {
            case Some(acc) => Option(acc.group(2))
            case _ => throw new AdaException(s"Error parsing access data set id: '$access'")
          })
      }

      /**
        * Get datasets local permission from global ids. ViewOnly permission settings
        * @param dataSetGlobalIds dataset global id list
        * @return Set of dataset ViewOnly permission
        */
      def getViewOnlyDateSetPermissions(dataSetGlobalIds: Seq[Option[String]]) = {
        for (dataSets <- dataSettingRepo.find(Seq("dataSetIdGlobalReference" #-> dataSetGlobalIds)))
          yield
            dataSets.flatMap(dataSet =>
              viewOnly.map(permission => s"DS:${dataSet.dataSetId}.$permission")).toSet
      }

      /**
        * Class containing information after permissions check
        * @param isPermissionsUpdate if permissions have been updated
        * @param permissions permissions list
        */
      case class PermissionInfo(isPermissionsUpdate: Boolean = false, permissions: Seq[String]);

      /**
        * Check if ViewOnly permission must be updated
        * @param localUserPermissions current local permissions
        * @param oidcUserPermissions permission from OIDC provider
        * @return permission info
        */
      def viewOnlyPermissionsCheck(localUserPermissions: Seq[String], oidcUserPermissions: Set[String]): PermissionInfo = {
        val notViewOnlyPermissions = localUserPermissions.filter(perm => viewOnly.forall(permView => !perm.endsWith(permView))).toSet
        val viewOnlyPermissions = localUserPermissions.filter(perm => viewOnly.exists(permView => perm.endsWith(permView))).toSet

        val viewOnlyDiff = viewOnlyPermissions.diff(oidcUserPermissions)
        val dataSetPermDiff = oidcUserPermissions.diff(viewOnlyPermissions)

        if(viewOnlyDiff.isEmpty && dataSetPermDiff.isEmpty)
          PermissionInfo(permissions = localUserPermissions)
        else
          PermissionInfo(isPermissionsUpdate = true, (notViewOnlyPermissions ++ oidcUserPermissions).toSeq.sorted)
      }

      /**
        * Manage existing user updating userId with oidcId,
        * username with old userId/username, name, email, permissions if necessary
        * @param existingUser user present in the database
        * @param oidcUser user from OpenID provider
        * @return Login information
        */
      def manageExistingUser(existingUser: User, oidcUser: User, dataSetPermissions: Set[String]) = {
        val permissionInfo = viewOnlyPermissionsCheck(existingUser.permissions, dataSetPermissions)

        if (existingUser.oidcUserName.isEmpty)
          updateUser(existingUser, oidcUser, permissionInfo.permissions)
        else if (existingUser.oidcUserName.isDefined &&
          (!existingUser.name.equalsIgnoreCase(oidcUser.name)
            || !existingUser.email.equalsIgnoreCase(oidcUser.email)
            || permissionInfo.isPermissionsUpdate))
          updateUser(existingUser, oidcUser, permissionInfo.permissions)
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
      def manageNewUser(oidcUser: User, dataSetPermissions: Set[String]) = {
          for {user <- userManager.findById(oidcUser.userId)
               result <- user.map(manageExistingUser(_, oidcUser, dataSetPermissions))
                 .getOrElse(addNewUser(oidcUser, dataSetPermissions))
               } yield result
      }

      def addNewUser(oidcUser: User, dataSetPermissions: Set[String]) =
        userRepo.save(oidcUser.copy(permissions = dataSetPermissions.toSeq.sorted)).flatMap(_ =>
          successfulResult(oidcUser, " new user imported."))

      def updateUser(existingUser: User, oidcUser: User, dataSetPermissions: Seq[String]) = {
        userRepo.update(
          existingUser.copy(
            userId = oidcUser.userId,
            oidcUserName = oidcUser.oidcUserName,
            name = oidcUser.name,
            email = oidcUser.email,
            permissions = dataSetPermissions
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
          dataSetPermissions <- getViewOnlyDateSetPermissions(parseDataSetGlobalIds(accessTokenOpt.get))
          result <- user
            .map(manageExistingUser(_, oidcUser, dataSetPermissions))
            .getOrElse(manageNewUser(oidcUser, dataSetPermissions))
        } yield result
      }
    }
  }
}
