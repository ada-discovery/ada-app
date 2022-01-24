package org.ada.server.util

import org.ada.server.AdaException

import scala.language.reflectiveCalls

object ManageResource {

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
        throw new AdaException("Cannot close resource. It is null.")

}
