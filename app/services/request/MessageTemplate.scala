package services.request

import java.util.Date

import models.{BatchRequestState, NotificationType}
import play.api.routing.Router
import reactivemongo.bson.BSONObjectID


object MessageTemplate {

  val adviceMessage = "Dear %s,\nas %s member, please be informed that the status of a request changed,\nyou can see the request in Ada at the following url %s\n\n" +
    "please find below the current status:" +
    " \nrequester: %s\ndataset Id: %s\non date: %s\nstatus change: from %s to %s\ndate of status change :%s\nupdated by user: %s\n\n" +
    "Best regards, Ada Team"


  val solicitationMessage = "Dear %s,\nas %s member, please be informed that the status of a request changed,\nyou are now required to do an action in Ada at the following url %s\n\n" +
    "please find below the current status:" +
    " \nrequester: %s\ndataset Id: %s\non date: %s\nstatus change:from %s to %s\ndate of status change: %s\nupdated by user: %s\n\n" +
    "Best regards, Ada Team"

  val solicitationSubject = "action needed on request in state %s"

  val adviceSubject = "status of request updated to state %s"

  def format(notificationType: NotificationType.Value, targetUser:String, userRole: String, createdByUser:String,dataSetId: String, requestDate:Date,  fromStatus: BatchRequestState.Value, toStatus:BatchRequestState.Value, dateOfChange:Date, updatedByUser:String, getRequestUrl:String)={

    notificationType match {
      case NotificationType.Solicitation => MessageTemplate.formatSolicitation(
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

      case NotificationType.Advice => MessageTemplate.formatAdvice(
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
  }

  def formatAdvice(targetUser:String, userRole: String, createdByUser:String, dataSetId: String,requestDate:Date,  fromStatus: BatchRequestState.Value, toStatus:BatchRequestState.Value, dateOfChange:Date, updatedByUser:String, getRequestUrl:String)={
    adviceMessage.format(targetUser,userRole, getRequestUrl ,createdByUser,dataSetId,requestDate,fromStatus,toStatus,dateOfChange,updatedByUser)
  }

  def formatSolicitation(targetUser:String, userRole: String, createdByUser:String, dataSetId: String,requestDate:Date,  fromStatus: BatchRequestState.Value, toStatus:BatchRequestState.Value, dateOfChange:Date, updatedByUser:String, getRequestUrl:String)={
    solicitationMessage.format(targetUser,userRole, getRequestUrl,createdByUser,dataSetId,requestDate,fromStatus,toStatus,dateOfChange,updatedByUser)
  }

  def formatSolicitationSubject(toStatus:BatchRequestState.Value )={
    solicitationSubject.format(toStatus)
  }

  def formatAdviceSubject(toStatus:BatchRequestState.Value)={
    adviceSubject.format(toStatus)
  }
}
