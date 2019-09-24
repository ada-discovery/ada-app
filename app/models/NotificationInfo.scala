package models

import java.util.Date

import play.api.libs.json.JsObject

object NotificationType extends Enumeration {
    val Advice, Solicitation = Value
}

case class NotificationInfo(
    notificationType: NotificationType.Value,
    dataSetId: String,
    creationDate: Date,
    createdByUser: String,
    targetUser: String,
    userRole: Role.Value,
    targetUserEmail: String,
    fromState: BatchRequestState.Value,
    toState: BatchRequestState.Value,
    possibleActions: Traversable[RequestAction.Value],
    updateDate: Date,
    updatedByUser: String,
    getRequestUrl: String,
    description: Option[String],
    items: Traversable[JsObject]
)