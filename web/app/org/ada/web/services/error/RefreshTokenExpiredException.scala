package org.ada.web.services.error

import org.ada.server.AdaException

case class RefreshTokenExpiredException(message: String, cause: Throwable) extends AdaException(message, cause) {
  def this(message: String) = this(message, null)
}
