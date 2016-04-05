package persistence

import com.google.inject.Singleton
import models.security.{UserManager, SecurityPermissionCache, SecurityRoleCache, CustomUser}
import play.api.libs.json.{Format, Json}
import reactivemongo.bson.BSONObjectID
import util.SecurityUtil

import play.modules.reactivemongo.json._
import scala.concurrent.ExecutionContext.Implicits._

import scala.concurrent.Future

/**
  * Merge of former UserManagerImpl with UserRepo
  *
  */
@Singleton
class CustomUserRepo(collectionName: String) extends MongoAsyncCrudRepo[CustomUser, BSONObjectID](collectionName) with UserManager{

  // TODO: dummy user profiles. eventually remove them.
  override val adminUser = new CustomUser(None, "admin user", "admin@mail", SecurityUtil.md5("123456"), "None", List(SecurityRoleCache.adminRole), SecurityPermissionCache.adminPermissions)
  override val basicUser = new CustomUser(None, "basic user", "basic@mail", SecurityUtil.md5("123456"), "None", List(SecurityRoleCache.basicRole), SecurityPermissionCache.basicPermissions)

  // add admin and basic users
  addUserIfNotPresent(adminUser)
  addUserIfNotPresent(basicUser)

  private def addUserIfNotPresent(user: CustomUser) =
    find(Some(Json.obj("name" -> user.name))).map{ users =>
      if (users.isEmpty)
        save(user)
    }

  /**
    * Given a mail, find the corresponding account.
    *
    * @param email mail to be matched.
    * @return Option containing Account with matching mail; None otherwise
    */
  override def findByEmail(email: String): Future[Option[CustomUser]] = {
    val usersFuture = find(Some(Json.obj("email" -> email)))
    usersFuture.map { users =>
      if (users.nonEmpty) Some(users.head) else None
    }
  }

  /**
    * Given an id, find the corresponding account.
    *
    * @param id ID to be matched.
    * @return Option containing Account with matching ID; None otherwise
    */
  override def findById(id: String): Future[Option[CustomUser]] = {
    val usersFuture = find(Some(Json.obj("name" -> id)))
    usersFuture.map { users =>
      if (users.nonEmpty) Some(users.head) else None
    }
  }

  // TODO: strub
  override def updateUser(user: CustomUser): Future[Boolean] = {
    Future(true)
  }

}
