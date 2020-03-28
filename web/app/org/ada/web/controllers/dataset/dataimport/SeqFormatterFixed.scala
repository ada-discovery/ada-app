package org.ada.web.controllers.dataset.dataimport

import play.api.data.FormError
import play.api.data.format.Formatter

// TODO: just temporary... should be promoted
final class SeqFormatterFixed[T](
  fromString: String => Option[T],
  toString: T => String = (x: T) => x.toString, // by default call toString
  delimiter: String = ",",                      // use comma as a default delimiter
  nonEmptyStringsOnly: Boolean = true
) extends Formatter[Seq[T]] {

  def bind(key: String, data: Map[String, String]) =
    try {
      data.get(key).map { string =>
        if (string.nonEmpty) {
          val strings = string.split(delimiter, -1).map(_.trim)

          val items = if (nonEmptyStringsOnly)
            strings.filter(_.nonEmpty).flatMap(fromString(_)).toSeq
          else
            strings.flatMap(fromString(_)).toSeq

          Right(items)
        } else
          Right(Nil)
      }.getOrElse(
        Left(List(FormError(key, s"No value found for the key '$key'")))
      )
    } catch {
      case e: Exception => Left(List(FormError(key, e.getMessage)))
    }

  def unbind(key: String, list: Seq[T]) =
    Map(key -> list.map(toString).mkString(s"$delimiter "))
}

object SeqFormatterFixed {

  def apply(nonEmptyStringsOnly: Boolean): Formatter[Seq[String]] = new SeqFormatterFixed[String](
    x => Some(x),
    nonEmptyStringsOnly = nonEmptyStringsOnly
  )
}
