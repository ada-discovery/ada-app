package models.json

import play.api.libs.json._

object EnumFormat  {

  private def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) => {
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException => JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not appear to contain the value: '$s'")
        }
      }
      case _ => JsError("String value expected")
    }
  }

  private def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(enumReads(enum), enumWrites)
  }
}


object OrdinalSortedEnumFormat  {

  private def enumReads[E <: Enumeration](
    idValueMap: Map[Int, E#Value]
  ): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsNumber(id) =>
        idValueMap.get(id.toInt).map(
          JsSuccess(_)
        ).getOrElse(
          JsError(s"Enumeration does not have enum value with (sorted) id $id.")
        )

      case _ => JsError("Number value expected")
    }
  }

  private def enumWrites[E <: Enumeration](
    valueIdMap: Map[E#Value, Int]
  ): Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsNumber(valueIdMap.get(v).get)
  }

  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    val valueIdMap: Map[E#Value, Int] = enum.values.toSeq.sortBy(_.toString).zipWithIndex.toMap

    Format(enumReads(valueIdMap.map(_.swap)), enumWrites(valueIdMap))
  }
}

//object Tupple2ArrayFormat  {
//
//  def enumReads[A,B](implicit a: Reads[A], b: Reads[B]): Reads[Tuple2[A, B]] = new Reads[Tuple2[A, B]] {
//    def reads(json: JsValue): JsResult[Tuple2[A, B]] = json match {
//      case JsAaray(s) => {
//        try {
//          JsSuccess(enum.withName(s))
//        } catch {
//          case _: NoSuchElementException => JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not appear to contain the value: '$s'")
//        }
//      }
//      case _ => JsError("String value expected")
//    }
//  }
//
//  implicit def enumWrites[A,B](implicit a: Writes[A], b: Writes[B]): Writes[Tuple2[A, B]] = new Writes[Tuple2[A, B]] {
//    def writes(tuple: Tuple2[A, B]) = JsObject(Seq(a.writes(tuple._1), b.writes(tuple._2)))
//  }
//
//  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
//    Format(EnumFormat.enumReads(enum), EnumFormat.enumWrites)
//  }
//}