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