package org.ada.server.models.datatrans

import java.util.Date

import org.ada.server.json.HasFormat
import org.ada.server.models.ScheduledTime
import org.ada.server.models.datatrans.DataSetTransformation._
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

case class ChangeFieldEnumsTransformation(
  _id: Option[BSONObjectID] = None,

  sourceDataSetId: String,
  fieldNameOldNewEnums: Seq[(String, String, String)],

  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends DataSetMetaTransformation {

  override val sourceDataSetIds = Seq(sourceDataSetId)

  override def copyCore(
    __id: Option[BSONObjectID],
    _timeCreated: Date,
    _timeLastExecuted: Option[Date],
    _scheduled: Boolean,
    _scheduledTime: Option[ScheduledTime]
  ) = copy(
    _id = __id,
    timeCreated = _timeCreated,
    timeLastExecuted = _timeLastExecuted,
    scheduled = _scheduled,
    scheduledTime = _scheduledTime
  )
}

object ChangeFieldEnumsTransformation extends HasFormat[ChangeFieldEnumsTransformation] {
  val format = Json.format[ChangeFieldEnumsTransformation]
}