package controllers.core

import java.util.concurrent.TimeoutException

import controllers.routes
import dataaccess.{AdaConversionException, AdaDataAccessException}
import models.AdaException
import org.incal.play.controllers.ExceptionHandler
import play.api.Logger
import play.api.mvc.{Request, Result}
import play.api.mvc.Results.Redirect

trait AdaExceptionHandler extends ExceptionHandler {

  override protected def handleExceptions(
    functionName: String,
    extraMessage: Option[String] = None)(
    implicit request: Request[_]
  ): PartialFunction[Throwable, Result] = {

    case _: TimeoutException =>
      handleTimeoutException(functionName, extraMessage)

    case e: AdaDataAccessException =>
      val message = s"Repo/db problem found while executing $functionName function${extraMessage.getOrElse("")}."
      Logger.error(message, e)
      Redirect(routes.AppController.index()).flashing("errors" -> message)

    case e: AdaConversionException =>
      val message = s"Conversion problem found while executing $functionName function${extraMessage.getOrElse("")}. Cause: ${e.getMessage}"
      handleBusinessException(message, e)

    case e: AdaException =>
      val message = s"General problem found while executing $functionName function${extraMessage.getOrElse("")}. Cause: ${e.getMessage}"
      handleBusinessException(message, e)

    case e: Throwable =>
      handleFatalException(functionName, extraMessage, e)
  }
}