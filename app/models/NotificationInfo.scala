package models

import java.util.Date

import org.ada.web.controllers.dataset.TableViewData
import reactivemongo.bson.BSONObjectID

object NotificationType extends Enumeration {
  val Advice, Solicitation = Value
}

case class NotificationInfo (notificationType: NotificationType.Value,
                             dataSetId: String,
                             creationDate:Date,
                             createdByUser:String,
                             targetUser:String,
                             userRole: Role.Value,
                             targetUserEmail:String,
                             fromState:BatchRequestState.Value,
                             toState:BatchRequestState.Value,
                             updateDate:Date,
                             updatedByUser:String,
                             getRequestUrl:String,
                             description: Option[String],
                             items: Option[TableViewData])