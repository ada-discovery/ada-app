package models

import java.util.Date

import org.ada.web.controllers.dataset.TableViewData
import reactivemongo.bson.BSONObjectID

object NotificationType extends Enumeration {
  val Advice, Solicitation = Value
}

case class NotificationInfo (val notificationType: NotificationType.Value,val dataSetId: String, val creationDate:Date,val createdByUser:String , val targetUser:String, val userRole: Role.Value, val targetUserEmail:String, val fromState:BatchRequestState.Value, val toState:BatchRequestState.Value, val updateDate:Date,
                             val updatedByUser:String, val getRequestUrl:String, val description: String, val items: Option[TableViewData])
