package models.sampleRequest

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}
import reactivemongo.bson.BSONObjectID

import scala.util.Try

object BSONObjectIdFormatJson extends Format[BSONObjectID]{
  override def writes(o: BSONObjectID): JsValue = JsString(o.toString)

  override def reads(json: JsValue): JsResult[BSONObjectID] = json match {
    case JsString(x) =>
      val maybeOID: Try[BSONObjectID] = BSONObjectID.parse(x)
      if (maybeOID.isSuccess)
        JsSuccess(maybeOID.get)
      else
        JsError("Expected BSONObjectID as JsString")
    case _ => JsError("Expected BSONObjectID as JsString")
  }
}
