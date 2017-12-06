package controllers

import play.api.data.FormError
import play.api.data.format.Formatter

private object StringSeqFormatter extends Formatter[Seq[String]] {

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

private object IntSeqFormatter extends Formatter[Seq[Int]] {

  def bind(key: String, data: Map[String, String]) = {
    try {
      data.get(key).map { string =>
        val items = string.split(",").map(_.trim).filter(_.nonEmpty).map(_.toInt).toSeq
        Right(items)
      }.getOrElse(
        Left(List(FormError(key, s"No value found for the key '$key'")))
      )
    } catch {
      case e: Exception => Left(List(FormError(key, e.getMessage)))
    }
  }

  def unbind(key: String, value: Seq[Int]) =
    Map(key -> value.mkString(", "))
}

private object DoubleSeqFormatter extends Formatter[Seq[Double]] {

  def bind(key: String, data: Map[String, String]) = {
    try {
      data.get(key).map { string =>
        val items = string.split(",").map(_.trim).filter(_.nonEmpty).map(_.toDouble).toSeq
        Right(items)
      }.getOrElse(
        Left(List(FormError(key, s"No value found for the key '$key'")))
      )
    } catch {
      case e: Exception => Left(List(FormError(key, e.getMessage)))
    }
  }

  def unbind(key: String, value: Seq[Double]) =
    Map(key -> value.mkString(", "))
}

object SeqFormatter {

  def apply: Formatter[Seq[String]] = StringSeqFormatter

  def applyInt: Formatter[Seq[Int]] = IntSeqFormatter

  def applyDouble: Formatter[Seq[Double]] = DoubleSeqFormatter
}
