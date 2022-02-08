package org.ada.server.util

import org.ada.server.AdaNotCloseResourceException
import play.api.Logger

import scala.concurrent.Future
import scala.language.reflectiveCalls

object ManageResource {

  private val logger = Logger

  def using[A <: { def close(): Unit}, B](resource: A)(f: A => B) : B =
      try{
        f(resource)
      } finally {
        closeResource(resource)
      }

  def closeResource[A <: { def close(): Unit}](resource: A): Unit =
    if (resource != null)
      resource.close()
    else
      throw new AdaNotCloseResourceException("Cannot close resource. It is null.")

  def closeResourceWithFutureFailed[A <: { def close(): Unit}](e: Exception, resource: A): Future[Unit] = e match {
    case err: AdaNotCloseResourceException => Future.failed(err)
    case _ =>
      try closeResource(resource) catch {case ex: Exception => logger.error("Error closing resource", ex)}
      Future.failed(e)
  }

}
