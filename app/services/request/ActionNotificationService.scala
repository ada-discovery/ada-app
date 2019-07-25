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

class ActionNotificationService @Inject()(mailerClient: MailerClient, pdfBuilder: PdfBuilder, messageBuilder: MessageBuilder) {
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
    val subject = messageBuilder.buildSubject(notification)
    val attachments = getAttachments(notification)
    val message = messageBuilder.buildBody(notification)

    val email = Email(
      from = fromEmail,
      to = Seq(notification.targetUserEmail),
      subject = subject,
      bodyHtml = Some(message),
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
}