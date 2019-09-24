package services.request

import java.util.Date

import models.{BatchRequestState, NotificationInfo, NotificationType}
import play.api.routing.Router
import reactivemongo.bson.BSONObjectID

object MessageTemplate {

  //TODO: To be on a safe side you would avoid using format with 10 params (in the order) and rather have named substitution with $
  //this object will probably be deleted when the final construction of the pdf attachment will be defined (the email is already using the html templates)

  def adviceMessage(notificationInfo: NotificationInfo) = s"Dear ${notificationInfo.targetUser},\nas ${notificationInfo.userRole} member, please be informed that the status of a request changed,\nyou can see the request in Ada at the following url ${notificationInfo.getRequestUrl}\n\n" +
    "please find below the current status:" +
    s" \nrequester: ${notificationInfo.targetUser}\ndataset Id: ${notificationInfo.dataSetId}\non date: ${notificationInfo.creationDate}%s\nstatus change: from ${notificationInfo.fromState} to ${notificationInfo.toState}\ndate of status change :${notificationInfo.updateDate}\nupdated by user: ${notificationInfo.updatedByUser}\n\n" +
    "Best regards, Ada Team"

  def solicitationMessage(notificationInfo: NotificationInfo) = s"Dear ${notificationInfo.targetUser},\nas ${notificationInfo.userRole} member, please be informed that the status of a request changed,\nyou are now required to do an action in Ada at the following url ${notificationInfo.getRequestUrl}\n\n" +
    "please find below the current status:" +
    s" \nrequester: ${notificationInfo.targetUser}\ndataset Id: ${notificationInfo.dataSetId}\non date: ${notificationInfo.creationDate}\nstatus change:from ${notificationInfo.fromState} to ${notificationInfo.toState}\ndate of status change: ${notificationInfo.updateDate}\nupdated by user: ${notificationInfo.updatedByUser}\n\n" +
    "Best regards, Ada Team"

  // TODO: Pls. use "private" for all non-public (non overridable) members and functions everywhere
  val solicitationSubject = "action needed on request in state %s"

  val adviceSubject = "status of request updated to state %s"

  // TODO: u should pass here "NotificationInfo" as a data holder... you're unpacking it too soon (similar to MessageBuilder)
  def format(
    notificationInfo: NotificationInfo
  )=
    notificationInfo.notificationType match {
      case NotificationType.Solicitation => solicitationMessage(notificationInfo)
      case NotificationType.Advice => adviceMessage(notificationInfo)
    }

  def formatSolicitationSubject(toStatus:BatchRequestState.Value)=
    solicitationSubject.format(toStatus)

  def formatAdviceSubject(toStatus:BatchRequestState.Value)=
    adviceSubject.format(toStatus)
}
