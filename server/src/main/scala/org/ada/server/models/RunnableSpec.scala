package org.ada.server.models

import java.util.Date

import org.ada.server.dataaccess.BSONObjectIdentity
import org.ada.server.json.EnumFormat
import play.api.libs.json.{Format, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

case class RunnableSpec(
  _id: Option[BSONObjectID],

  input: Map[String, String],
  runnableClassName: String,
  name: String,

  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends Schedulable

object RunnableSpec {
  implicit val weekDayFormat = EnumFormat(WeekDay)
  implicit val scheduleTimeFormat = Json.format[ScheduledTime]

  implicit val format = Json.format[RunnableSpec]

  implicit object RunnableSpecIdentity extends BSONObjectIdentity[RunnableSpec] {
    def of(entity: RunnableSpec): Option[BSONObjectID] = entity._id
    protected def set(entity: RunnableSpec, id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}