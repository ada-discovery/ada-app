package models

import java.util.Date

import reactivemongo.bson.BSONObjectID

object NotificationType extends Enumeration {
  val Advice, Solicitation = Value
}

case class NotificationInfo (val notificationType: NotificationType.Value,val requestId: BSONObjectID, val creationDate:Date,val createdByUser:String , val targetUser:String, val userRole: Role.Value, val targetUserEmail:String, val fromState:BatchRequestState.Value, val toState:BatchRequestState.Value, val updateDate:Date, val updatedByUser:String)
