

package models.security

import javax.inject.{Singleton, Inject}

import com.google.inject.ImplementedBy
import persistence.CustomUserRepo
import persistence.RepoTypes.UserRepo
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import util.SecurityUtil



@ImplementedBy(classOf[UserManagerImpl])
trait UserManager {

  /**
    * Matches email and password for authentification.
    * Returns an Account, if successful.
    * @param email Mail for matching.
    * @param password Password which should match the password associated to the mail.
    * @return None, if password is wrong or not associated mail was found. Corresponding Account otherwise.
    */
  def authenticate(email: String, password: String): Future[Boolean] = {
    val pwHash = SecurityUtil.md5(password)
    findByEmail(email).map(userOp =>
      userOp match{
        case Some(usr) => (usr.password == pwHash)
        case None => false
      }
    )
  }

  /**
    * Match userid with permission.
    * @param userid usrid, most commonly mail
    * @param permission permission to be checked.
    * @return
    */
  def authorize(userid: String, permission: String): Future[Boolean] = {
    val userOpFuture: Future[Option[CustomUser]] = findByEmail(userid)
    userOpFuture.map{ (userOp: Option[CustomUser]) =>
      userOp match{
        case Some(usr) => usr.permissions.contains(permission)
        case None => false
      }
    }
  }

  def updateUser(user: CustomUser): Future[Boolean]

  def findById(id: String): Future[Option[CustomUser]]
  def findByEmail(email: String): Future[Option[CustomUser]]

  // TODO: dummy user profiles. eventually remove them.
  def adminUser: CustomUser = CustomUser(None, "admin user", "admin@mail", SecurityUtil.md5("123456"), "None", List(SecurityRoleCache.adminRole), SecurityPermissionCache.adminPermissions)
  def basicUser: CustomUser = CustomUser(None, "basic user", "basic@mail", SecurityUtil.md5("123456"), "None", List(SecurityRoleCache.basicRole), SecurityPermissionCache.basicPermissions)
}

/**
  * Class for managing and accessing Users.
  */
@Singleton
private class UserManagerImpl @Inject()(userRepo: UserRepo) extends UserManager {

  // add admin and basic users
  addUserIfNotPresent(adminUser)
  addUserIfNotPresent(basicUser)

  private def addUserIfNotPresent(user: CustomUser) =
    userRepo.find(Some(Json.obj("name" -> user.name))).map{ users =>
      if (users.isEmpty)
        userRepo.save(user)
    }


  /**
    * Given a mail, find the corresponding account.
    *
    * @param email mail to be matched.
    * @return Option containing Account with matching mail; None otherwise
    */
  override def findByEmail(email: String): Future[Option[CustomUser]] = {
    val usersFuture = userRepo.find(Some(Json.obj("email" -> email)))
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

  /**
    * Update the user by looking up the username in the database and changing the other fields.
    * @param user
    * @return
    */
  override def updateUser(user: CustomUser): Future[Boolean] = {
    userRepo.update(user)
    Future(true)
  }
}

