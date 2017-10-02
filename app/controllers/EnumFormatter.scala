package controllers

import play.api.data.FormError
import play.api.data.format.Formatter
import play.api.libs.json.{JsError, JsSuccess}

import scala.reflect.ClassTag

object EnumFormatter {

  def apply[E <: Enumeration](enum: E) = new Formatter[E#Value] {

    def bind(key: String, data: Map[String, String]) = {
      try {
        data.get(key).map(value =>
          Right(enum.withName(value))
        ).getOrElse(
          Left(List(FormError(key, s"No value found for the key '$key'")))
        )
      } catch {
        case e: Exception => Left(List(FormError(key, e.getMessage)))
      }
    }

    def unbind(key: String, value: E#Value) =
      Map(key -> value.toString)
  }
}

private class JavaEnumFormatter[E <: Enum[E]](implicit classTag: ClassTag[E]) extends Formatter[E] {

  private val clazz = classTag.runtimeClass.asInstanceOf[Class[E]]

  def bind(key: String, data: Map[String, String]) = {
    try {
      data.get(key).map(value =>
        try {
          Right(Enum.valueOf[E](clazz, value))
        } catch {
          case _: IllegalArgumentException => Left(List(FormError(key, s"Enumeration expected of type: '${clazz.getName}', but it does not appear to contain the value: '$value'")))
        }
      ).getOrElse(
        Left(List(FormError(key, s"No value found for the key '$key'")))
      )
    } catch {
      case e: Exception => Left(List(FormError(key, e.getMessage)))
    }
  }

  def unbind(key: String, value: E) =
    Map(key -> value.toString)
}

object JavaEnumFormatter {

  def apply[E <: Enum[E]](implicit classTag: ClassTag[E]): Formatter[E] = new JavaEnumFormatter[E]
}