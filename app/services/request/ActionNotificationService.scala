package services.request

import java.io.File

import javax.inject.Inject
import models.{NotificationInfo, NotificationType, Role}
import org.apache.commons.mail.EmailException
import play.api.{Configuration, Logger}
import play.api.libs.mailer.{AttachmentFile, Email, MailerClient}
import org.incal.core.util.toHumanReadableCamel
import scala.collection.mutable.ListBuffer

class ActionNotificationService @Inject()(configuration: Configuration, mailerClient: MailerClient, pdfBuilder: PdfBuilder) {
  protected val logger = Logger

  val fromEmail= configuration.getString("notification-admin-email").getOrElse("no-reply@uni.lu")

  def sendNotifications(notifications: Option[Traversable[NotificationInfo]])= {

    val attachmentsByUser : Map[String, Option[AttachmentFile]] = notifications.map {
      notification =>
        notification.map(n => (n.targetUser, buildAttachment(n)))
    }.get.toMap

    notifications.map {
      _.foreach { n =>
        sendNotification(n, attachmentsByUser.get(n.targetUser).flatten)
      }
    }

    attachmentsByUser.values.map{
      _.foreach { a => a.file.delete()}
    }
  }

  def sendNotification(notification:NotificationInfo, attachmentOption: Option[AttachmentFile])= {
    val subject = buildSubject(notification)
    val attachments = attachmentOption.toSeq
    val message = buildMessage(notification)

    val email = Email(
      from = fromEmail,
      to = Seq(notification.targetUserEmail),
      subject = subject,
      bodyHtml = Some(message),
      attachments = attachments
    )

    try {
      mailerClient.send(email)
    } catch {
      case e: EmailException => logger.error(message, e)
    }
  }

  // TODO: why to introduce a single-line function for a single caller?
  def isResumeRequired(role: Role.Value, notificationType: NotificationType.Value)={
    (role == Role.Committee || role ==  Role.Owner) && (notificationType == NotificationType.Solicitation)
  }

  def getAttachments(notificationInfo: NotificationInfo)= {
    if(isResumeRequired(notificationInfo.userRole, notificationInfo.notificationType) ) {
      Some(buildResumeDocument(notificationInfo))
    }  else {
      None
    }
  }

  def buildAttachment(notificationInfo: NotificationInfo)= {
    if(isResumeRequired(notificationInfo.userRole, notificationInfo.notificationType) ) {
      Some(buildResumeDocument(notificationInfo))
    }  else {
      None
    }
  }

  def buildResumeDocument(notificationInfo: NotificationInfo)= {
    val resumeFile =  pdfBuilder.getFile(notificationInfo)
    AttachmentFile("request-resume.pdf", resumeFile)
  }

  def buildMessage(notification: NotificationInfo)={
    notification.notificationType match {
      case NotificationType.Solicitation =>  views.html.requests.notification.solicitationTemplate(notification).toString()
      case NotificationType.Advice => views.html.requests.notification.adviceTemplate(notification).toString()
    }
  }

  def buildSubject(notification: NotificationInfo) =
    notification.notificationType match {
      case NotificationType.Solicitation => "action needed on request in state " +  toHumanReadableCamel(notification.toState.toString())
      case NotificationType.Advice => "status of request updated to state " + toHumanReadableCamel(notification.toState.toString())
    }
}