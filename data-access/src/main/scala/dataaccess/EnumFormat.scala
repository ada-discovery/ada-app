package dataaccess

import play.api.libs.json._

object EnumFormat  {

  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
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

  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(EnumFormat.enumReads(enum), EnumFormat.enumWrites)
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