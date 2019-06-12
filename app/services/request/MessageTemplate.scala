package services.request

import java.util.Date

import models.BatchRequestState
import reactivemongo.bson.BSONObjectID

object MessageTemplate {

  val requestUrl = "requests/%s/get"

  val message = "Dear %s,\nas %s member, please be informed that the status of the request number %s changed,\nyou can see the request in Ada at the following url %s\n\n" +
    "please find below the current status:" +
    " \nrequester:%s\non date:%s\nstatus change:from %s to %s\ndate of status change:%s\nupdated by user: %s\n\n" +
    "Best regards, Ada Team"

  def format(targetUser:String, userRole: String, requestId:BSONObjectID, createdByUser:String, requestDate:Date,  fromStatus: BatchRequestState.Value, toStatus:BatchRequestState.Value, dateOfChange:Date, updatedByUser:String)={
    message.format(targetUser,userRole, requestId.stringify, requestUrl.format(requestId.stringify),createdByUser,requestDate,fromStatus,toStatus,dateOfChange,updatedByUser)
  }
}
