package services.request

import models.{NotificationInfo, NotificationType}

class MessageBuilder {

  def buildBody(notification: NotificationInfo) = {
    notification.notificationType match {
      case NotificationType.Solicitation => buildSolicitation(notification)
      case NotificationType.Advice => buildAdvice(notification)
    }
    }

  def buildSubject(notification: NotificationInfo) = {
    notification.notificationType match {
      case NotificationType.Solicitation => "action needed on request in state " + notification.toState
      case NotificationType.Advice => "status of request updated to state " + notification.toState
    }
  }

  def buildSolicitation(notification: NotificationInfo) = {
    views.html.requests.notification.solicitationTemplate(
      notification.targetUser,
      notification.userRole,
      notification.getRequestUrl,
      notification.createdByUser,
      notification.dataSetId,
      notification.creationDate,
      notification.fromState,
      notification.toState,
      notification.updateDate,
      notification.updatedByUser
    ).toString()
  }

  def buildAdvice(notification: NotificationInfo) = {
    views.html.requests.notification.adviceTemplate(
      notification.targetUser,
      notification.userRole,
      notification.getRequestUrl,
      notification.createdByUser,
      notification.dataSetId,
      notification.creationDate,
      notification.fromState,
      notification.toState,
      notification.updateDate,
      notification.updatedByUser
    ).toString()
  }
}