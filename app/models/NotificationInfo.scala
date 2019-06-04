package models

import java.util.Date

import reactivemongo.bson.BSONObjectID

case class NotificationInfo (val requestId: BSONObjectID, val creationDate:Date, val targetUser:String, val targetUserEmail:String, val fromState:BatchRequestState.Value, val toState:BatchRequestState.Value, val updateDate:Date, val updatedByUser:String)
