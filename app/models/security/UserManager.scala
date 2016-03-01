package models.security

import javax.inject.{Singleton, Inject}

import com.google.inject.ImplementedBy
import persistence.RepoTypeRegistry.UserRepo
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import util.SecurityUtil


@ImplementedBy(classOf[UserManagerImpl])
trait UserManager {
  def authenticate(email: String, password: String): Future[Option[CustomUser]]
  def findById(id: String): Future[Option[CustomUser]]

  // TODO: remove
  def adminUser: CustomUser
  def basicUser: CustomUser
}

/**
  * Class for managing and accessing Users.
  */
@Singleton
private class UserManagerImpl @Inject()(userRepo: UserRepo) extends UserManager {

  // TODO: dummy user profiles. eventually remove them.
  override val adminUser = new CustomUser(None, "admin user", "admin@mail", SecurityUtil.md5("123456"), "None", List(SecurityRoleCache.adminRole), SecurityPermissionCache.adminPermissions)
  override val basicUser = new CustomUser(None, "basic user", "basic@mail", SecurityUtil.md5("123456"), "None", List(SecurityRoleCache.basicRole), SecurityPermissionCache.basicPermissions)

  // add admin and basic users
  addUserIfNotPresent(adminUser)
  addUserIfNotPresent(basicUser)

  private def addUserIfNotPresent(user: CustomUser) =
    userRepo.find(Some(Json.obj("name" -> user.name))).map{ users =>
      if (users.isEmpty)
        userRepo.save(user)
    }

//  /**
//    * Given a mail, find the corresponding account.
//    *
//    * @param email mail to be matched.
//    * @return Option containing Account with matching mail; None otherwise
//    */
//  def findByEmail(email: String): Option[CustomUser] = {
//    userList.find((usr: CustomUser) => (usr.email == email))
//  }

  /**
   * Matches email and password for authentification.
   * Returns an Account, if successful.
   *
   * @param email Mail for matching.
   * @param password Password which should match the password associated to the mail.
   * @return None, if password is wrong or not associated mail was found. Corresponding Account otherwise.
   */
  override def authenticate(email: String, password: String): Future[Option[CustomUser]] = {
    val usersFuture = userRepo.find(Some(Json.obj("email" -> email, "password" -> SecurityUtil.md5(password))))
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
    val usersFuture = userRepo.find(Some(Json.obj("name" -> id)))
    usersFuture.map { users =>
      if (users.nonEmpty) Some(users.head) else None
    }
  }
}
