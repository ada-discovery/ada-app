package controllers.core

import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Flash, Request}

case class WebContext(implicit val flash: Flash, val msg: Messages, val request: Request[_])

object WebContext {
  implicit def toFlash(
    implicit webContext: WebContext
  ): Flash = webContext.flash

  implicit def toMessages(
    implicit webContext: WebContext
  ): Messages = webContext.msg

  implicit def toRequest(
    implicit webContext: WebContext
  ): Request[_] = webContext.request

  implicit def apply(
    messagesApi: MessagesApi)(
    implicit request: Request[_]
  ): WebContext = {
    implicit val msg = messagesApi.preferred(request)
    implicit val flash = request.flash
    WebContext()
  }
}