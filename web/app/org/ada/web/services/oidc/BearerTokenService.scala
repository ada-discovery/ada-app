package org.ada.web.services.oidc


import com.google.inject.ImplementedBy
import org.ada.server.AdaParseException
import org.ada.web.models.BearerToken
import play.api.Configuration
import play.api.libs.json.{JsError, JsSuccess}
import play.api.libs.ws.WSClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@ImplementedBy(classOf[BearerTokenServiceImpl])
trait BearerTokenService {
  def getBearerToken: Future[BearerToken]
}

@Singleton
class BearerTokenServiceImpl @Inject()(
   configuration: Configuration,
   ws: WSClient
 ) extends BearerTokenService {

  private val oidcTokenEndPoint = configuration.getString("oidc.tokenEndPointUrl").get
  private val oidcClientId = configuration.getString("oidc.clientId").get
  private val oidcSecret = configuration.getString("oidc.secret").get

  override def getBearerToken: Future[BearerToken] = {
    ws.url(oidcTokenEndPoint)
      .post(Map("grant_type" -> Seq("client_credentials"),
        "client_id" -> Seq(oidcClientId),
        "client_secret" -> Seq(oidcSecret)))
      .map(response => response.json.validate[BearerToken] match {
        case s: JsSuccess[BearerToken] => s.get
        case e: JsError => throw AdaParseException("Error parsing bearer token", new Throwable(JsError.toJson(e).toString()))
      })
  }
}

