package org.ada.web.services

import com.google.inject.ImplementedBy
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.UserSettingsRepo
import org.ada.server.models.UserSettings
import reactivemongo.bson.BSONObjectID

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[UserSettingsServiceImpl])
trait UserSettingsService {

  def getUsersSettings: Future[UserSettings]

  def updateUsersSettings(userSettings: UserSettings) : Future[BSONObjectID]

}

@Singleton
class UserSettingsServiceImpl @Inject()(
  userSettingsRepo: UserSettingsRepo
  ) extends UserSettingsService {

  override def getUsersSettings: Future[UserSettings] = {
    for {
      userSettingsRes <- userSettingsRepo.find()
    } yield {
      if (userSettingsRes.size != 1)
        throw new AdaException("Error must exist only one user_settings document")
      else
        userSettingsRes.head
    }
  }

  override def updateUsersSettings(userSettings: UserSettings): Future[BSONObjectID] = {
    val userSettingsId = userSettings._id.getOrElse(throw new AdaException("UserSettings id does is empty."))
    for {
      currentUserSettingsRes <- userSettingsRepo.get(userSettingsId)
      updateUserSettingsRes <- {
        val currentUserSettings = currentUserSettingsRes.getOrElse(throw new AdaException(s"UserSettings with id $userSettingsId not found."))
        userSettingsRepo.update(currentUserSettings.copy(defaultLoginRights = userSettings.defaultLoginRights))
      }
    } yield
      updateUserSettingsRes
  }
}
