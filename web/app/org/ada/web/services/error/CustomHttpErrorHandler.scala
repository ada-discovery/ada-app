package org.ada.web.services.error

import controllers.Assets.Redirect
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.{RequestHeader, Result}
import play.api.routing.Router
import play.api.{Configuration, Environment, OptionalSourceMapper}

import javax.inject.{Inject, Provider}
import scala.concurrent.Future


class CustomHttpErrorHandler @Inject()(
                                      environment: Environment,
                                      configuration: Configuration,
                                      sourceMapper: OptionalSourceMapper,
                                      router: Provider[Router]
                                    ) extends DefaultHttpErrorHandler(environment, configuration, sourceMapper, router) {

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = exception match {
    case e: RefreshTokenExpiredException => Future.successful(
      Redirect(org.ada.web.controllers.routes.AuthController.accessExternalResourceInfo(e.message)))
    case _ => super.onServerError(request, exception)
  }

}