package models

import java.util.Date

import org.ada.web.controllers.dataset.TableViewData
import reactivemongo.bson.BSONObjectID

object NotificationType extends Enumeration {
  val Advice, Solicitation = Value
}

case class NotificationInfo (val notificationType: NotificationType.Value,val dataSetId: String, val creationDate:Date,val createdByUser:String , val targetUser:String, val userRole: Role.Value, val targetUserEmail:String, val fromState:BatchRequestState.Value, val toState:BatchRequestState.Value, val updateDate:Date,
                             val updatedByUser:String, val getRequestUrl:String, val description: Option[String], val items: Option[TableViewData])



 object NotificationBuilder {
  private var notificationType: NotificationType.Value = null
   private var dataSetId: String = null
   private var creationDate: Date = null
   private var createdByUser: String = null
   private var targetUser: String = null
   private var userRole: Role.Value = null
   private  var targetUserEmail: String = null
   private var fromState :BatchRequestState.Value = null
   private var toState: BatchRequestState.Value = null
   private var updateDate: Date = null
   private  var updatedByUser: String = null
   private  var getRequestUrl: String = null
   private var  description: Option[String] = None
   private var  items: Option[TableViewData] = None


  def withNotificationType(t:NotificationType.Value) = {
    notificationType = t;
    this
  }

  def withDataSetId(dataSetId: String) = {
    this.dataSetId = dataSetId;
    this
  }

  def withCreationDate(creationDate: Date) = {
    this.creationDate = creationDate;
    this
  }

  def withCreatedByUser(createdByUser: String) = {
    this.createdByUser = createdByUser;
    this
  }


  def withTargetUser(targetUser: String) = {
    this.targetUser = targetUser;
    this
  }


  def withUserRole(userRole: Role.Value) = {
    this.userRole = userRole;
    this
  }
  def withTargetUserEmail(targetUserEmail: String) = {
    this.targetUserEmail = targetUserEmail;
    this
  }

  def withFromState(fromState: BatchRequestState.Value) = {
    this.fromState = fromState;
    this
  }


  def withToState(toState: BatchRequestState.Value) = {
    this.toState = toState;
    this
  }

  def withUpdateDate(updateDate: Date) = {
    this.updateDate = updateDate;
    this
  }

  def withGetRequestUrl(getRequestUrl: String) = {
    this.getRequestUrl = getRequestUrl;
    this
  }

  def withUpdatedByUser(updatedByUser: String) = {
    this.updatedByUser = updatedByUser;
    this
  }

   def withDescription(description: Option[String]) = {
     this.description = description;
     this
   }

  def withItems(items: Option[TableViewData]) = {
    this.items = items;
    this
  }

   def build() = NotificationInfo(notificationType,dataSetId,creationDate,createdByUser, targetUser,userRole, targetUserEmail, fromState, toState, updateDate,updatedByUser, getRequestUrl, description, items)
}