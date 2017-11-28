package controllers.core

import be.objectify.deadbolt.scala.AuthenticatedRequest
import controllers.WebJarAssets
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Flash, Request}

case class WebContext(implicit val flash: Flash, val msg: Messages, val request: AuthenticatedRequest[_], val webJarAssets: WebJarAssets)

object WebContext {
  implicit def toFlash(
    implicit webContext: WebContext
  ): Flash = webContext.flash

  implicit def toMessages(
    implicit webContext: WebContext
  ): Messages = webContext.msg

  implicit def toRequest(
    implicit webContext: WebContext
  ): AuthenticatedRequest[_] = webContext.request

  implicit def toWebJarAssets(
    implicit webContext: WebContext
  ): WebJarAssets = webContext.webJarAssets

  implicit def apply(
    messagesApi: MessagesApi,
    webJarAssets: WebJarAssets)(
    implicit request: AuthenticatedRequest[_]
  ): WebContext = {
    implicit val msg = messagesApi.preferred(request)
    implicit val flash = request.flash
    implicit val webJars = webJarAssets
    WebContext()
  }
}