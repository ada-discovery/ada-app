package services.request

import models.{BatchOrderRequest, BatchRequestState, NotificationInfo, Action}
import BatchRequestState._
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.services.UserManager
import play.api.libs.mailer.{Email, MailerClient}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.BatchOrderRequestRepo

import scala.collection.mutable.ListBuffer

class ActionNotificationService @Inject()(
                                           mailerClient: MailerClient
                                         ) {

   var notificationsss:List[String]= List()
  var notifications= ListBuffer[NotificationInfo]()
  val fromEmail="emanuele.raffero@uni.lu"
  val messageHtml = "<html><p>Dear %s, <br>the status of the request number %s changed :%s</p></html>"
  val message = "Dear %s, \nthe status of the request number %s changed,\nyou can see the request in Ada at the following url %s\n" +
    "please find below the current status" +
    " \n\nrequester:%s\non date:%s\nstatus change:from %s to $s\ndate of status change:%s\nupdated by user: %s\n" +
    "Best regards, Ada Team"

 def addNotification(notification: NotificationInfo) = {
   notifications+=notification
  }

  def sendNotifications()={
    notifications.foreach(n=>sendNotification(n))
  }

  def sendNotification(notification:NotificationInfo)={
    println("sending notification")

    val message = MessageTemplate.format(
      notification.targetUser,
      notification.userRole.toString,
      notification.requestId,
      notification.createdByUser,
      notification.creationDate,
      notification.fromState,
      notification.toState,
      notification.updateDate,
      notification.updatedByUser)

    val email = Email(
      from = fromEmail,
      to = Seq(notification.targetUserEmail),
      subject = "status of request "+ notification.requestId.stringify+ " updated to state "+ notification.toState,
      bodyText = Some(message)
    )

    mailerClient.send(email)
  }
}
