package services.request

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import models.{NotificationInfo, NotificationType, Role}
import org.apache.commons.mail.EmailException
import play.api.libs.mailer.{AttachmentFile, Email, MailerClient}
import play.api.{Configuration, Logger}
import org.incal.core.util.toHumanReadableCamel
import util.PdfHelper

@ImplementedBy(classOf[ActionNotificationServiceImpl])
trait ActionNotificationService {
    def sendNotifications(notifications: Traversable[NotificationInfo])
}

@Singleton
class ActionNotificationServiceImpl @Inject()(
    configuration: Configuration,
    mailerClient: MailerClient
) extends ActionNotificationService {
    protected val logger = Logger

    private val fromEmail = configuration.getString("notification-admin-email").getOrElse("no-reply@uni.lu")

    def sendNotifications(notifications: Traversable[NotificationInfo]) = {

        val attachmentsByUser: Map[String, Option[AttachmentFile]] = notifications.map(n => (n.targetUser, buildAttachment(n))).toMap

        notifications.foreach(n => sendNotification(n, attachmentsByUser.get(n.targetUser).flatten))

        attachmentsByUser.values.map {
            _.foreach { a => a.file.delete() }
        }
    }

    private def sendNotification(notification: NotificationInfo, attachmentOption: Option[AttachmentFile]) = {
        val subject = buildSubject(notification)
        val attachments = attachmentOption.toSeq
        val message = views.html.requests.notification.emailTemplate(notification).toString

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

    private def buildAttachment(notificationInfo: NotificationInfo) = {
        def isResumeRequired(role: Role.Value, notificationType: NotificationType.Value) = {
            (role == Role.Committee || role == Role.BioBank) && (notificationType == NotificationType.Solicitation)
        }

        if (isResumeRequired(notificationInfo.userRole, notificationInfo.notificationType)) {
            Some(buildResumeDocument(notificationInfo))
        } else {
            None
        }
    }

    private def buildResumeDocument(notificationInfo: NotificationInfo) = {
        val resumeFile = PdfHelper.getFile(notificationInfo)
        AttachmentFile("request-resume.pdf", resumeFile)
    }

    private def buildSubject(notification: NotificationInfo) =
        (notification.notificationType match {
            case NotificationType.Solicitation => "[action required]"
            case NotificationType.Advice => "[status updated]"
        }) + " for sample request"
}