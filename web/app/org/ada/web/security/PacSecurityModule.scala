package org.ada.web.security

import com.google.inject.AbstractModule
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.store.{PlayCacheStore, PlaySessionStore}
import org.pac4j.play.{ApplicationLogoutController, CallbackController}
import play.api.{Configuration, Environment}

class PacSecurityModule (environment: Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {
    var oidcConfiguration = new OidcConfiguration();

    oidcConfiguration.setClientId(configuration.getString("oidc.clientId").orNull)
    oidcConfiguration.setSecret(configuration.getString("oidc.secret").orNull)
    oidcConfiguration.setClientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
    oidcConfiguration.setPreferredJwsAlgorithm(JWSAlgorithm.RS256)
    oidcConfiguration.setDiscoveryURI(configuration.getString("oidc.discoveryURI").orNull)
    var oidcClient = new OidcClient[OidcProfile](oidcConfiguration);

    val adaBaseUrl = configuration.getString("oidc.adaBaseUrl").orNull
    val clients = new Clients(s"$adaBaseUrl/oidc/callback", oidcClient)
    val config = new Config(clients)
    config.setHttpActionAdapter(new CustomHttpActionAdapter)
    bind(classOf[Config]).toInstance(config)

    bind(classOf[PlaySessionStore]).to(classOf[PlayCacheStore])

    val callbackController = new CallbackController()
    callbackController.setDefaultUrl("/?defaulturlafterlogout")
    callbackController.setMultiProfile(true)
    bind(classOf[CallbackController]).toInstance(callbackController)

    // logout
    val logoutController = new ApplicationLogoutController()
    logoutController.setDefaultUrl(adaBaseUrl)
    bind(classOf[ApplicationLogoutController]).toInstance(logoutController)

  }
}
