package controllers

import play.api.i18n.Messages
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
}