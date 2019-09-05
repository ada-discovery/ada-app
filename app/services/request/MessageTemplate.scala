package services.request

import java.util.Date

import models.{BatchRequestState, NotificationType}
import play.api.routing.Router
import reactivemongo.bson.BSONObjectID

object MessageTemplate {

  //TODO: To be on a safe side you would avoid using format with 10 params (in the order) and rather have named substitution with $
  //this object will probably be deleted when the final form of the pdf attachment will be defined (the email is already using the html templates)

  val adviceMessage = "Dear %s,\nas %s member, please be informed that the status of a request changed,\nyou can see the request in Ada at the following url %s\n\n" +
    "please find below the current status:" +
    " \nrequester: %s\ndataset Id: %s\non date: %s\nstatus change: from %s to %s\ndate of status change :%s\nupdated by user: %s\n\n" +
    "Best regards, Ada Team"

  val solicitationMessage = "Dear %s,\nas %s member, please be informed that the status of a request changed,\nyou are now required to do an action in Ada at the following url %s\n\n" +
    "please find below the current status:" +
    " \nrequester: %s\ndataset Id: %s\non date: %s\nstatus change:from %s to %s\ndate of status change: %s\nupdated by user: %s\n\n" +
    "Best regards, Ada Team"

  // TODO: Pls. use "private" for all non-public (non overridable) members and functions everywhere
  val solicitationSubject = "action needed on request in state %s"

  val adviceSubject = "status of request updated to state %s"

  // TODO: u should pass here "NotificationInfo" as a data holder... you're unpacking it too soon (similar to MessageBuilder)
  def format(
    notificationType: NotificationType.Value,
    targetUser:String,
    userRole: String,
    createdByUser:String,
    dataSetId: String,
    requestDate:Date,
    fromStatus: BatchRequestState.Value,
    toStatus:BatchRequestState.Value,
    dateOfChange:Date,
    updatedByUser:String,
    getRequestUrl:String
  )=
    notificationType match {
      case NotificationType.Solicitation => formatSolicitation(
        targetUser,
        userRole.toString,
        createdByUser,
        dataSetId,
        requestDate,
        fromStatus,
        toStatus,
        dateOfChange,
        updatedByUser,
        getRequestUrl)

      case NotificationType.Advice => formatAdvice(
        targetUser,
        userRole.toString,
        createdByUser,
        dataSetId,
        requestDate,
        fromStatus,
        toStatus,
        dateOfChange,
        updatedByUser,
        getRequestUrl)
    }

  // TODO: What's the point of taking 10 params and passing it to another function with 10 params?... remove
  def formatAdvice(
    targetUser:String,
    userRole: String,
    createdByUser:String,
    dataSetId: String,
    requestDate:Date,
    fromStatus: BatchRequestState.Value,
    toStatus:BatchRequestState.Value,
    dateOfChange:Date,
    updatedByUser:String,
    getRequestUrl:String
  )=
    adviceMessage.format(targetUser,userRole, getRequestUrl, createdByUser, dataSetId, requestDate, fromStatus, toStatus, dateOfChange, updatedByUser)

  // TODO: Same as above...remove
  def formatSolicitation(targetUser:String, userRole: String, createdByUser:String, dataSetId: String,requestDate:Date,  fromStatus: BatchRequestState.Value, toStatus:BatchRequestState.Value, dateOfChange:Date, updatedByUser:String, getRequestUrl:String)= {
    solicitationMessage.format(targetUser,userRole, getRequestUrl,createdByUser,dataSetId,requestDate,fromStatus,toStatus,dateOfChange,updatedByUser)
  }

  def formatSolicitationSubject(toStatus:BatchRequestState.Value)=
    solicitationSubject.format(toStatus)

  def formatAdviceSubject(toStatus:BatchRequestState.Value)=
    adviceSubject.format(toStatus)
}
