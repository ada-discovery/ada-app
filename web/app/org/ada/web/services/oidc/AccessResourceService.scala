package org.ada.web.services.oidc


import com.google.inject.ImplementedBy
import com.nimbusds.jwt.JWTParser
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils
import com.nimbusds.openid.connect.sdk.token.OIDCTokens
import org.ada.server.AdaException
import org.ada.server.models.User
import org.ada.web.models.JwtTokenInfo
import org.ada.web.services.error.RefreshTokenExpiredException
import play.api.{Configuration, Logger}
import play.api.cache.CacheApi
import play.api.libs.ws.{WSClient, WSResponse}
import play.cache.NamedCache
import play.mvc.Http

import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@ImplementedBy(classOf[AccessResourceServiceImpl])
trait AccessResourceService {


  def accessResource(user: User,
                     callResource: String => Future[WSResponse]): Future[WSResponse]

  def accessResource[T](user: User,
                        requestBody: T,
                        callResource: (String, T) => Future[WSResponse]): Future[WSResponse]

  def accessResource[T, P](user: User,
                           requestBody: T,
                           additionalParam: P,
                           callResource: (String, T, P) => Future[WSResponse]): Future[WSResponse]

}

@Singleton
class AccessResourceServiceImpl @Inject()(
   configuration: Configuration,
   ws: WSClient,
   @NamedCache("jwt-user-cache") jwtUserCache: CacheApi
 ) extends AccessResourceService {

  private val oidcTokenEndPoint = configuration.getString("oidc.tokenEndPointUrl").getOrElse(
    new AdaException("Configuration issue: 'oidc.tokenEndPointUrl' was not found in the configuration file.")
  )
  private val oidcClientId = configuration.getString("oidc.clientId").getOrElse(
    new AdaException("Configuration issue: 'oidc.clientId' was not found in the configuration file.")
  )
  private val oidcClientSecret= configuration.getString("oidc.secret").getOrElse(
    new AdaException("Configuration issue: 'oidc.secret' was not found in the configuration file.")
  )


  override def accessResource(user: User,
                              callResource: String => Future[WSResponse]): Future[WSResponse] = {
    for {
      jwtToken <- manageJwtToken(getUserJwtToken(user))
      res <- callResource(getAuthHeader(jwtToken.accessToken))
    } yield
      checkResourceResponse(res)
  }

  override def accessResource[T](user: User,
                                 requestBody: T,
                                 callResource: (String, T) => Future[WSResponse]): Future[WSResponse] = {

    for {
      jwtToken <- manageJwtToken(getUserJwtToken(user))
      res <- callResource(getAuthHeader(jwtToken.accessToken), requestBody)
    } yield
      checkResourceResponse(res)
  }



  override def accessResource[T,P](user: User,
                                   requestBody: T,
                                   additionalParam: P,
                                   callResource: (String, T, P) => Future[WSResponse]): Future[WSResponse] = {
    for {
      jwtToken <- manageJwtToken(getUserJwtToken(user))
      res <- callResource(getAuthHeader(jwtToken.accessToken), requestBody, additionalParam)
    } yield
      checkResourceResponse(res)

  }


  private def checkResourceResponse(response: WSResponse): WSResponse = response match {
    case response if response.status >= Http.Status.OK && response.status <= Http.Status.MULTIPLE_CHOICES => response
    case _ => throw new AdaException("Failed to retrieve resource: http status " + response.status + ", body: "+ response.body)
  }


  private def getUserJwtToken(user: User): JwtTokenInfo = jwtUserCache.get[JwtTokenInfo](user.userId).getOrElse(
    throw new AdaException(s"JWT token not found for userId ${user.userId}")
  )


  private def getAuthHeader(bearerAccessToken: BearerAccessToken): String = s"Bearer ${bearerAccessToken.getValue}"

  /**
    * Manage jwtToken if expired
    * @param jwtTokenInfo
    * @return JwtTokenInfo updated if is necessary
    */
  private def manageJwtToken(jwtTokenInfo: JwtTokenInfo): Future[JwtTokenInfo] = {
    val jwtAccessTokenClaim = JWTParser.parse(jwtTokenInfo.accessToken.getValue).getJWTClaimsSet
    if(jwtAccessTokenClaim.getExpirationTime.after(new Date()))
      Future(jwtTokenInfo)
    else {
      ws.url(s"$oidcTokenEndPoint")
        .post(Map("grant_type" -> Seq("refresh_token"),
          "client_id" -> Seq(s"$oidcClientId"),
          "client_secret" -> Seq(s"$oidcClientSecret"),
          "refresh_token" -> Seq(jwtTokenInfo.refreshToken.getValue)))
        .map(response => {
          if(response.status == Http.Status.OK) {
            val oidcToken = OIDCTokens.parse(JSONObjectUtils.parse(response.json.toString()))
            val jwtTokenInfo = JwtTokenInfo(oidcToken.getBearerAccessToken, oidcToken.getRefreshToken)
            jwtUserCache.set(jwtAccessTokenClaim.getSubject, jwtTokenInfo)
            jwtTokenInfo
          } else {
            Logger.warn("Refresh token expired for user " + jwtAccessTokenClaim.getSubject + ". Logout/Login needed.")
            jwtUserCache.remove(jwtAccessTokenClaim.getSubject)
            throw new RefreshTokenExpiredException("OpenId Refresh token expired. Please logout and login again.")
          }
        })
    }
  }


}






