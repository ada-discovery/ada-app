package org.ada.web.controllers

import org.ada.server.models.{ScheduledTime, WeekDay}
import org.incal.core.util.hasNonAlphanumericUnderscore
import org.incal.play.formatters.EnumFormatter
import play.api.data.Forms.{mapping, nonEmptyText, number, of, optional}
import play.api.data.Mapping

trait MappingHelper {

  private implicit val weekDayFormatter = EnumFormatter(WeekDay)

  protected val scheduledTimeMapping: Mapping[ScheduledTime] = mapping(
    "weekDay" -> optional(of[WeekDay.Value]),
    "hour" -> optional(number(min = 0, max = 23)),
    "minute" -> optional(number(min = 0, max = 59)),
    "second" -> optional(number(min = 0, max = 59))
  )(ScheduledTime.apply)(ScheduledTime.unapply)

  private val upperCasePattern = "[A-Z]".r

  protected val dataSetIdMapping = nonEmptyText.verifying(
    "Data Set Id must not contain any non-alphanumeric characters (except underscore)",
    dataSetId => !hasNonAlphanumericUnderscore(dataSetId.replaceFirst("\\.",""))
  ).verifying(
    "Data Set Id must not contain any upper case letters",
    dataSetId => !upperCasePattern.findFirstIn(dataSetId).isDefined
  )
}
