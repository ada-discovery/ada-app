package services.request

import java.io.File

import javax.inject.Inject
import models.{NotificationInfo, NotificationType, Role}
import org.apache.commons.mail.EmailException
import play.api.{Configuration, Logger}
import play.api.libs.mailer.{AttachmentFile, Email, MailerClient}

import scala.collection.mutable.ListBuffer

class ActionNotificationService @Inject()(configuration: Configuration, mailerClient: MailerClient, pdfBuilder: PdfBuilder, messageBuilder: MessageBuilder) {
  protected val logger = Logger

  // TODO: This service is stateful, which is extremely dangerous and by looking at the code also not needed. The caller can create a list of notifications himself
  // TODO: Also using "var" must be super justified and normally should be avoided at any cost
  // TODO: Finally, having "tempFiles" as a "var" is a crime :)
  var notifications = ListBuffer[Option[NotificationInfo]]()
  var tempFiles = ListBuffer[File]()
  val fromEmail= configuration.getString("notification-admin-email").getOrElse("no-reply@uni.lu")

  def addNotification(notification: Option[NotificationInfo]) = {
   notifications+=notification
  }

  def cleanNotifications() = {
    notifications.clear()
  }

  def sendNotifications()= {
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
    // TODO: I've seen it used xx times in the code but pls. never use matching on a boolean condition... super redundant and beats the purpose of pattern matching
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