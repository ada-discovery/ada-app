package org.ada.web.controllers

import com.nimbusds.jwt.JWTParser
import com.nimbusds.oauth2.sdk.token.{BearerAccessToken, RefreshToken}
import jp.t2v.lab.play2.auth.Login
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, UserRepo}
import org.ada.server.models.{LoginRights, User, UserSettings}
import org.ada.server.services.UserManager
import org.ada.web.controllers.UserDataSetPermissions.{standard, viewOnly}
import org.ada.web.models.JwtTokenInfo
import org.ada.web.security.AdaAuthConfig
import org.ada.web.services.UserSettingsService
import org.incal.core.dataaccess.Criterion._
import org.incal.play.security.SecurityRole
import org.pac4j.core.config.Config
import org.pac4j.core.profile._
import org.pac4j.play.scala._
import org.pac4j.play.store.PlaySessionStore
import play.api.cache.CacheApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.{Configuration, Logger}
import play.cache.NamedCache
import play.libs.concurrent.HttpExecutionContext

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
  userSettingsService: UserSettingsService,
  @NamedCache("jwt-user-cache") jwtUserCache: CacheApi
) extends Controller
    with Security[CommonProfile]            // PAC4J
    with Login                              // Play2 Auth
    with AdaAuthConfig {                    // Play2 Auth


  private val clientId =  configuration.getString("oidc.clientId").getOrElse(
    new AdaException("Configuration issue: 'oidc.clientId' was not found in the configuration file.")
  )

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

  private val realmAccessAttribute =  configuration.getString("oidc.realmAccessAttribute").getOrElse(
    new AdaException("Configuration issue: 'oidc.realmAccessAttribute' was not found in the configuration file.")
  )

  private val resourceAccessAttribute =  configuration.getString("oidc.resourceAccessAttribute").getOrElse(
    new AdaException("Configuration issue: 'oidc.resourceAccessAttribute' was not found in the configuration file.")
  )

  private val roleAdminName = configuration.getString("oidc.roleAdminName").getOrElse(
    new AdaException("Configuration issue: 'oidc.roleAdminName' was not found in the configuration file.")
  )

  private val dataSetGlobalIdPrefix = configuration.getString("oidc.dataSetGlobalIdPrefix").getOrElse(
    new AdaException("Configuration issue: 'oidc.dataSetGlobalIdPrefix' was not found in the configuration file.")
  )

  private val dataSetGlobalIdRegexStr = configuration.getString("oidc.dataSetGlobalIdRegex").getOrElse(
    new AdaException("Configuration issue: 'oidc.dataSetGlobalIdRegex' was not found in the configuration file.")
  )

  private val dataSetGlobalIdRegex = new Regex(s"$dataSetGlobalIdRegexStr")


  def oidcLogin: Action[AnyContent] = Secure("OidcClient") { profiles =>
    Action.async { implicit request =>

      def successfulResult(user: User, extraMessage: String = "") = {
        Logger.info(s"Successful authentication for the user '${user.userId}', id '${user.oidcUserName}' using the associated OIDC provider.$extraMessage")
        gotoLoginSucceeded(user.userId)
      }

      case class RolesDataSetIdsInfo(roles: Seq[String], dataSetIdsGlobalRef: Set[String])

      /**
        * Parse JWT access token to get user roles and dataSetGlobalIdsReference
        * @param accessToken access token
        * @return Roles and Global ids sequence (DataCatalog)
        */
      def parseRolesAndDataSetIdsGlobalReference(accessToken: BearerAccessToken): RolesDataSetIdsInfo = {
        val jsonAccessToken = Json.parse(JWTParser.parse(accessToken.getValue).getJWTClaimsSet.toString)

        def evalAccessRoles(roles: Option[JsValue]): Set[String] = roles match {
          case Some(rolesAttrJson) => rolesAttrJson.as[Set[String]]
          case None => Set()
        }

        val realmAccessRoles = evalAccessRoles((jsonAccessToken \ s"$realmAccessAttribute" \ s"$rolesAttribute").toOption)
        val resourceAccessRoles = evalAccessRoles((jsonAccessToken \ s"$resourceAccessAttribute" \ s"$clientId" \ s"$rolesAttribute").toOption)
        val oidcRoles = realmAccessRoles ++ resourceAccessRoles

        val dataSetIdsGlobalRef = oidcRoles.filter(_.startsWith(s"$dataSetGlobalIdPrefix"))
          .map(access => dataSetGlobalIdRegex.findFirstMatchIn(access) match {
            case Some(acc) => acc.group(2)
            case _ => throw new AdaException(s"Error parsing access data set id: '$access'")
          })
        val userAdminRole = oidcRoles.filter(_ == s"$roleAdminName").map(_ => SecurityRole.admin).toSeq

        RolesDataSetIdsInfo(userAdminRole, dataSetIdsGlobalRef)
      }

      def createPermissions(dataSetsIds: Set[String], defaultLoginRights: LoginRights.Value) =
        defaultLoginRights match {
          case LoginRights.viewOnly => generatePermissionsFromTemplate(dataSetsIds, viewOnly)
          case LoginRights.standard => generatePermissionsFromTemplate(dataSetsIds, standard)
          case _ => throw new AdaException(s"Default rights: $defaultLoginRights not found")
      }

      def generatePermissionsFromTemplate(dataSetsIds: Set[String], permissionTemplate: Seq[String]) =
        dataSetsIds.flatMap(dataSetId => permissionTemplate.map(permission => s"DS:${dataSetId}.$permission"))


      /**
        * Class containing datasets ids
        * @param dataSetIdsWithGlobalIdRef datasets with global id reference
        * @param dataSetIdsOidc datasets with global id reference given by OIDC provider
        */
      case class DataSetIds(dataSetIdsWithGlobalIdRef: Set[String],
                                 dataSetIdsOidc: Set[String])

      /**
        * Get datasets ids with global id reference and from OIDC provider
        * @param dataSetIdsGlobalRef dataset global id reference list
        * @return DataSetIds class
        */
      def getDateSetIds(dataSetIdsGlobalRef: Set[String]) = {

        for (dataSets <- dataSettingRepo.find(Seq("dataSetIdGlobalReference" #!= None)))
          yield {
            val dataSetIdsWithGlobalIdRef = dataSets.map(_.dataSetId).toSet
            val dataSetIdsOidc = dataSets
              .filter(dataSet => dataSetIdsGlobalRef.contains(dataSet.dataSetIdGlobalReference.get))
              .map(_.dataSetId).toSet
            DataSetIds(dataSetIdsWithGlobalIdRef, dataSetIdsOidc)
          }
      }

      /**
        * Class containing information after permissions check
        * @param isPermissionsUpdate if permissions have been updated
        * @param permissions permissions list
        */
      case class PermissionInfo(isPermissionsUpdate: Boolean = false, permissions: Seq[String]);

      /**
        * Verify permission according OIDC provider and local permission settings
        * @param localUserPermissions current local permissions
        * @param dataSetIds dataset ids
        * @return permission info
        */
      def verifyPermissions(localUserPermissions: Seq[String],
                            dataSetIds: DataSetIds,
                            userSettings: UserSettings): PermissionInfo = {

        val dataSetIdsWithGlobalIdRef = dataSetIds.dataSetIdsWithGlobalIdRef
        val dataSetIdsOidc = dataSetIds.dataSetIdsOidc

        val userLocalPermissionWithGlobalIdRef = {
          for {
            dataSetId <- dataSetIdsWithGlobalIdRef
            permissions <- localUserPermissions if permissions.contains(dataSetId)
          } yield
            (dataSetId, permissions)
        }.groupBy(_._1)
          .map(dataSetIdPermission => (dataSetIdPermission._1, dataSetIdPermission._2.map(_._2)))

        val userLocalPermissionWithoutGlobalIdRef = localUserPermissions
          .filter(permission => dataSetIdsWithGlobalIdRef.forall(dataSetId => !permission.contains(dataSetId)))

        val userLocalDataSetIdsWithGlobalIdRef = userLocalPermissionWithGlobalIdRef.keys.toSet
        val userLocalDataSetIdsWithGloabalIdRefDiff = userLocalDataSetIdsWithGlobalIdRef.diff(dataSetIdsOidc)
        val dataSetIdsOidcDiff = dataSetIdsOidc.diff(userLocalDataSetIdsWithGlobalIdRef)

        if(userLocalDataSetIdsWithGloabalIdRefDiff.isEmpty && dataSetIdsOidcDiff.isEmpty)
          PermissionInfo(permissions = localUserPermissions)
        else {
          val userLocalPermissionOidcIntersection = userLocalPermissionWithGlobalIdRef
            .filter(userPerm => dataSetIdsOidc.contains(userPerm._1))
            .flatMap(_._2)

          PermissionInfo(isPermissionsUpdate = true,
            (userLocalPermissionWithoutGlobalIdRef.toSet ++
              userLocalPermissionOidcIntersection.toSet ++
              createPermissions(dataSetIdsOidcDiff, userSettings.defaultLoginRights)).toSeq.sorted)
        }
      }


      /**
        * Manage existing user updating userId with oidcId,
        * username with old userId/username, name, email, roles and permissions if necessary
        * @param existingUser user present in the database
        * @param oidcUser user from OpenID provider
        * @param dataSetIds datasets ids
        * @return Login information
        */
      def manageExistingUser(existingUser: User,
                             oidcUser: User,
                             dataSetIds: DataSetIds,
                             userSettings: UserSettings) = {

        val permissionInfo = verifyPermissions(existingUser.permissions, dataSetIds, userSettings)
        val isRoleNotChange = existingUser.roles.diff(oidcUser.roles).isEmpty && oidcUser.roles.diff(existingUser.roles).isEmpty

        if (existingUser.oidcUserName.isEmpty)
          updateUser(existingUser, oidcUser, permissionInfo.permissions)
        else if (existingUser.oidcUserName.isDefined &&
          (!existingUser.name.equalsIgnoreCase(oidcUser.name)
            || !existingUser.email.equalsIgnoreCase(oidcUser.email)
            || permissionInfo.isPermissionsUpdate
            || !isRoleNotChange))
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
      def manageNewUser(oidcUser: User, dataSetIds: DataSetIds, userSettings: UserSettings) = {
          for {user <- userManager.findById(oidcUser.userId)
               result <- user.map(manageExistingUser(_, oidcUser, dataSetIds, userSettings))
                 .getOrElse(addNewUser(oidcUser, createPermissions(dataSetIds.dataSetIdsOidc, userSettings.defaultLoginRights)))
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
            roles = oidcUser.roles,
            permissions = dataSetPermissions
          )
        ).flatMap(_ =>
          successfulResult(oidcUser)
        )
      }

      val profile = profiles.head
      val userEmail = profile.getEmail
      val oidcIdOpt = Option(profile.getAttribute(s"$subAttribute", classOf[String]))
      val accessTokenOpt = Option(profile.getAttribute(s"$accessTokenAttribute", classOf[BearerAccessToken]))
      val refreshTokenOpt = Option(profile.getAttribute(s"$refreshTokenAttribute", classOf[RefreshToken]))

      if(oidcIdOpt.isEmpty || accessTokenOpt.isEmpty || refreshTokenOpt.isEmpty) {
        val errorMessage =
          s"OIDC login cannot be fully completed. The user '${userEmail} doesn't have one of these attributes ${subAttribute}, ${accessTokenAttribute}, ${refreshTokenAttribute} in Jwt token."
        logger.warn(errorMessage)
        Future(Redirect(routes.AppController.index()).flashing("errors" -> errorMessage))
      } else {

        val rolesDataSetIdsInfo = parseRolesAndDataSetIdsGlobalReference(accessTokenOpt.get)

        val oidcUser = User(
          userId = oidcIdOpt.get,
          oidcUserName = Option(profile.getUsername),
          name = profile.getDisplayName,
          email = userEmail,
          roles = rolesDataSetIdsInfo.roles
        )

        jwtUserCache.set(oidcUser.userId, JwtTokenInfo(accessTokenOpt.get, refreshTokenOpt.get))

        for {
          user <- userManager.findByEmail(userEmail)
          dataSetIds <- getDateSetIds(rolesDataSetIdsInfo.dataSetIdsGlobalRef)
          userSettings <- userSettingsService.getUsersSettings
          result <- user
            .map(manageExistingUser(_, oidcUser, dataSetIds, userSettings))
            .getOrElse(manageNewUser(oidcUser, dataSetIds, userSettings))
        } yield result
      }
    }
  }
}
