package org.ada.server.models.dataimport

import java.util.Date

import org.ada.server.json.HasFormat
import org.ada.server.models.{DataSetSetting, DataView, ScheduledTime}
import org.ada.server.models.dataimport.DataSetImport._
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

case class EGaitDataSetImport(
  _id: Option[BSONObjectID] = None,
  dataSpaceName: String,
  dataSetId: String,
  dataSetName: String,
  importRawData: Boolean = false,
  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  setting: Option[DataSetSetting] = None,
  dataView: Option[DataView] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends DataSetImport {

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

object EGaitDataSetImport extends HasFormat[EGaitDataSetImport] {
  val format = Json.format[EGaitDataSetImport]
}