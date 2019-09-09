package models

import java.util.Date

import play.api.libs.json.JsObject

object NotificationType extends Enumeration {
    val Advice, Solicitation = Value
}

// TODO: why do you need TableViewData as an optional attribute? If you want to pass items pass just that
// the item values are needed only when you need to produce the pdf, it could be a possible optimisation to query them and passing to this class
// only when a pdf has to be built
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
    //                             items: Option[TableViewData])
    items: Option[Traversable[JsObject]]
)