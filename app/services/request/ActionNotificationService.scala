package services.request

import javax.inject.Inject
import models.{NotificationInfo, NotificationType}
import play.api.libs.mailer.{Email, MailerClient}

import scala.collection.mutable.ListBuffer

class ActionNotificationService @Inject()(mailerClient: MailerClient) {
  var notifications= ListBuffer[NotificationInfo]()
  val fromEmail="emanuele.raffero@uni.lu"

 def addNotification(notification: NotificationInfo) = {
   notifications+=notification
  }

  def cleanNotifications() = {
    notifications.clear()
  }

  def sendNotifications()={
    notifications.foreach(n=>sendNotification(n))
  }

  def sendNotification(notification:NotificationInfo)={
    val message = getMessage(notification)
    val subject = getSubject(notification)

    val email = Email(
      from = fromEmail,
      to = Seq(notification.targetUserEmail),
      subject = subject,
      bodyText = Some(message)
    )

    mailerClient.send(email)
  }

  def getMessage(notification:NotificationInfo)= {

    notification.notificationType match {
      case NotificationType.Solicitation => MessageTemplate.formatSolicitation(
        notification.targetUser,
        notification.userRole.toString,
        notification.requestId,
        notification.createdByUser,
        notification.creationDate,
        notification.fromState,
        notification.toState,
        notification.updateDate,
        notification.updatedByUser,
        notification.getRequestUrl)

      case NotificationType.Advice => MessageTemplate.formatAdvice(
        notification.targetUser,
        notification.userRole.toString,
        notification.requestId,
        notification.createdByUser,
        notification.creationDate,
        notification.fromState,
        notification.toState,
        notification.updateDate,
        notification.updatedByUser,
        notification.getRequestUrl)
    }
  }

  def getSubject(notification:NotificationInfo)={

    notification.notificationType match {
      case NotificationType.Solicitation => MessageTemplate.formatSolicitationSubject(
               notification.requestId,
               notification.toState
      )

      case NotificationType.Advice => MessageTemplate.formatAdviceSubject(
        notification.requestId,
        notification.toState)
    }
  }
}