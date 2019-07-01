package services.request

import java.io.{ByteArrayOutputStream, File}

import javax.inject.Inject
import models.{NotificationInfo, NotificationType, Role}
import org.apache.pdfbox.pdmodel.PDDocument
import play.api.libs.mailer.{AttachmentFile, Email, MailerClient}
import play.libs.mailer.Attachment
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import scala.collection.mutable.ListBuffer
import java.io.File
import org.apache.pdfbox.pdmodel.PDPage

class ActionNotificationService @Inject()(mailerClient: MailerClient, pdfBuilder: PdfBuilder) {
  var notifications = ListBuffer[Option[NotificationInfo]]()
  var tempFiles = ListBuffer[File]()
  val fromEmail="emanuele.raffero@uni.lu"

 def addNotification(notification: Option[NotificationInfo]) = {
   notifications+=notification
  }

  def cleanNotifications() = {
    notifications.clear()
  }

  def sendNotifications()={
    notifications.map {
     _.foreach { n =>
        sendNotification(n)
      }
    }
    tempFiles.foreach(_.delete())
  }

  def sendNotification(notification:NotificationInfo)={
    val message = getMessage(notification)
    val subject = getSubject(notification)
    val attachments = getAttachments(notification)

    val email = Email(
      from = fromEmail,
      to = Seq(notification.targetUserEmail),
      subject = subject,
      bodyText = Some(message),
      attachments = attachments
    )

    mailerClient.send(email)
  }

def isResumeRequired(role: Role.Value, notificationType: NotificationType.Value)={
  (role == Role.Committee || role ==  Role.Owner) && (notificationType == NotificationType.Solicitation)
}

  def getAttachments(notificationInfo: NotificationInfo)= {
      val requestResume = isResumeRequired(notificationInfo.userRole, notificationInfo.notificationType) match {
      case true => Some(buildResumeDocument(notificationInfo))
      case false => None
    }

    Seq(requestResume).filter(a=>a.isDefined).map(a=>a.get)
  }

  def buildResumeDocument(notificationInfo: NotificationInfo)= {
    val resumeFile =  pdfBuilder.getFile(notificationInfo)
    tempFiles += resumeFile
    AttachmentFile("request-resume.pdf", resumeFile)
  }

  def getMessage(notification:NotificationInfo)= {
    MessageTemplate.format(
      notification.notificationType ,
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