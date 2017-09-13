package controllers

import play.api.libs.json.{JsObject, _}
import _root_.util.JsonUtil
import dataaccess.{AdaConversionException, FieldType}
import models.json.EitherFormat

class FlattenFormat[T](val format: Format[T], delimiter: String = ".") extends Format[T] {

  override def reads(json: JsValue): JsResult[T] = {
    val newJson = json match {
      case jsObject: JsObject => JsonUtil.deflatten(jsObject, delimiter)
      case json => json
    }

    format.reads(newJson)
  }

  override def writes(json: T): JsValue =
    format.writes(json) match {
      case jsObject: JsObject => JsonUtil.flatten(jsObject, delimiter)
      case json => json
    }
}

private class Tuple2Format[A, B](
    implicit val firstFormat: Format[A], secondFormat: Format[B]
  ) extends Format[(A, B)] {

  override def reads(json: JsValue): JsResult[(A, B)] = {
    val first = firstFormat.reads(json)
    val second = secondFormat.reads(json)

    if (first.isSuccess && second.isSuccess)
      JsSuccess((first.get, second.get))
    else
      JsError(s"Unable to read Tuple2 type from JSON $json")
  }

  override def writes(o: (A, B)): JsValue =
    JsArray(Seq(
      firstFormat.writes(o._1),
      secondFormat.writes(o._2)
    ))
}

private class Tuple3Format[A, B, C](
    implicit val firstFormat: Format[A], secondFormat: Format[B], thirdFormat: Format[C]
  ) extends Format[(A, B, C)] {

  override def reads(json: JsValue): JsResult[(A, B, C)] = {
    val first = firstFormat.reads(json)
    val second = secondFormat.reads(json)
    val third = thirdFormat.reads(json)

    if (first.isSuccess && second.isSuccess && third.isSuccess)
      JsSuccess((first.get, second.get, third.get))
    else
      JsError(s"Unable to read Tuple3 type from JSON $json")
  }

  override def writes(o: (A, B, C)): JsValue =
    JsArray(Seq(
      firstFormat.writes(o._1),
      secondFormat.writes(o._2),
      thirdFormat.writes(o._3)
    ))
}

object TupleFormat {
  implicit def apply[A: Format, B: Format]: Format[(A, B)] = new Tuple2Format[A, B]
  implicit def apply[A: Format, B: Format, C: Format]: Format[(A, B, C)] = new Tuple3Format[A, B, C]
}

private class OptionalFieldTypeFormat[T](fieldType: FieldType[T]) extends Format[Option[T]] {

  override def reads(json: JsValue): JsResult[Option[T]] =
    try {
      JsSuccess(fieldType.jsonToValue(json))
    } catch {
      case e: AdaConversionException => JsError(e.getMessage)
    }

  override def writes(o: Option[T]): JsValue =
    fieldType.valueToJson(o)
}

private class FieldTypeFormat[T](fieldType: FieldType[T]) extends Format[T] {

  override def reads(json: JsValue): JsResult[T] =
    try {
      fieldType.jsonToValue(json) match {
        case Some(x) => JsSuccess(x)
        case None => JsError(s"No value found for JSON $json")
      }
    } catch {
      case e: AdaConversionException => JsError(e.getMessage)
    }

  override def writes(o: T): JsValue =
    try {
      fieldType.valueToJson(Some(o))
    } catch {
      case e: ClassCastException => throw new AdaConversionException(s"Wrong type detected for the field type ${fieldType.spec.fieldType} and value ${o.toString}. Cause: ${e.getMessage}")
    }
}

object FieldTypeFormat {
  def applyOptional[T](fieldType: FieldType[T]): Format[Option[T]] = new OptionalFieldTypeFormat[T](fieldType)
  def apply[T](fieldType: FieldType[T]): Format[T] = new FieldTypeFormat[T](fieldType)
}