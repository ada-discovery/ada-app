package services.request

import java.util.Date

import models.BatchRequestState
import reactivemongo.bson.BSONObjectID

object MessageTemplate {

  val requestUrl = "requests/%s/get"

  val message = "Dear %s, \nthe status of the request number %s changed,\nyou can see the request in Ada at the following url %s\n\n" +
    "please find below the current status:" +
    " \nrequester:%s\non date:%s\nstatus change:from %s to %s\ndate of status change:%s\nupdated by user: %s\n\n" +
    "Best regards, Ada Team"

  def format(targetUser:String, requestId:BSONObjectID, requester:String, requestDate:Date, fromStatus: BatchRequestState.Value, toStatus:BatchRequestState.Value, dateOfChange:Date, updatedByUser:String)={
    message.format(targetUser,requestId.stringify,requestUrl.format(requestId.stringify),requester,requestDate,fromStatus,toStatus,dateOfChange,updatedByUser)
  }
}
