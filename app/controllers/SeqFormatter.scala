package controllers

import play.api.data.FormError
import play.api.data.format.Formatter

object SeqFormatter {

  def apply = new Formatter[Seq[String]] {

    def bind(key: String, data: Map[String, String]) = {
      try {
        data.get(key).map { string =>
          val items = string.split(",").map(_.trim).filter(_.nonEmpty).toSeq
          Right(items)
        }.getOrElse(
          Left(List(FormError(key, s"No value found for the key '$key'")))
        )
      } catch {
        case e: Exception => Left(List(FormError(key, e.getMessage)))
      }
    }

    def unbind(key: String, value: Seq[String]) = {
      Map(key -> value.mkString(", "))
    }
  }
}
