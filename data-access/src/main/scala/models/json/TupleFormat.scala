package models.json

import dataaccess.{AdaConversionException, FieldType}
import play.api.libs.json.{JsObject, _}

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