package org.ada.web.services.oidc


import com.google.inject.{ImplementedBy, Provides}
import com.nimbusds.oauth2.sdk.token.{BearerAccessToken, RefreshToken}
import org.ada.server.{AdaException, AdaParseException}
import org.ada.server.models.User
import org.ada.web.models.JwtTokenInfo
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.libs.json.{JsError, JsSuccess}
import play.api.libs.ws.{WSClient, WSResponse}
import play.cache.NamedCache
import play.mvc.Http

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



trait AccesResourceService {
  def accessResource(user: User, callResource: String => Future[WSResponse]): Future[WSResponse]
}


class AccesResourceServiceImpl @Inject()(
   configuration: Configuration,
   ws: WSClient,
   @NamedCache("jwt-user-cache") jwtUserCache: CacheApi
 ) extends AccesResourceService {

  private val oidcTokenEndPoint = configuration.getString("oidc.tokenEndPointUrl").get
  private val oidcClientId = configuration.getString("oidc.clientId").get
  private val oidcSecret = configuration.getString("oidc.secret").get

  override def accessResource(user: User, callResource: String => Future[WSResponse]): Future[WSResponse] = {
    //=====> Change implementation check id the access token is expired renew before call
    for {
      res <- callResource(getAuthHeader(getUserJwtToken(user).accessToken))
    } yield {
      res match {
        case res if isGoodResponse(res.status) => res
        case res if isUnauthorizedForbiddenResponse(res.status) => accessResourceWithRefreshedAccessToken(user, callResource)
        case _ => //RiseError
      }
    }
  }


  def accessResourceWithRefreshedAccessToken(user: User, callResource: String => Future[WSResponse]) = {
    //If refreshtoken expired login again Redirect

    for {
      jwtToken <- refreshAccessToken(getUserJwtToken(user).refreshToken)
      res <- callResource(getAuthHeader(jwtToken.accessToken))
    } yield {
      res match {
        case res if isGoodResponse(res.status) => res
        case _ => //Rise error
      }
    }
  }

  def getUserJwtToken(user: User): JwtTokenInfo = jwtUserCache.get[JwtTokenInfo](user.userId).getOrElse(
    new AdaException(s"JWT token not found for userId ${user.userId}")
  )

  def isGoodResponse(httpStatus: Int): Boolean = httpStatus >= Http.Status.OK || httpStatus <= Http.Status.MULTIPLE_CHOICES

  def isUnauthorizedForbiddenResponse(httpStatus: Int): Boolean = httpStatus == Http.Status.UNAUTHORIZED || httpStatus == Http.Status.FORBIDDEN

  def getAuthHeader(bearerAccessToken: BearerAccessToken): String = s"Bearer ${bearerAccessToken.getValue}"

  /**
    * Update cacheJwt, check if we reuse beaer token class, Remeber to treat expired token
    * @param refreshToken
    * @return
    */
  def refreshAccessToken(refreshToken: RefreshToken): Future[JwtTokenInfo] = ???
  //If refreshtoken expired login again Redirect
  /*ws.url(oidcTokenEndPoint)
    .post(Map("grant_type" -> Seq("refresh_token"),
      "client_id" -> Seq(oidcClientId),
      "refresh_token" -> Seq(refreshToken.getValue)))
    .map(response => response.json.validate[BearerToken] match {
      case s: JsSuccess[BearerToken] => s.get
      case e: JsError => throw AdaParseException("Error parsing bearer token", new Throwable(JsError.toJson(e).toString()))
    })*/



}






