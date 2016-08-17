package models.security

import javax.inject.{Singleton, Inject}

import dataaccess.Criterion
import Criterion.CriterionInfix
import com.google.inject.ImplementedBy
import ldap.{LdapSettings, LdapUserService, LdapConnector}
import persistence.RepoTypes.UserRepo

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import dataaccess.User

@ImplementedBy(classOf[UserManagerImpl])
trait UserManager {

  /**
    * Matches email and password for authentification.
    * Returns an Account, if successful.
 *
    * @param id ID (e.g. mail) for matching.
    * @param password Password which should match the password associated to the mail.
    * @return None, if password is wrong or not associated mail was found. Corresponding Account otherwise.
    */
  def authenticate(id: String, password: String): Future[Boolean] = {
    findByEmail(id).map(userOp =>
      userOp match {
        case Some(usr) => true //(usr.password == password)
        case None => false
      }
    )
  }

  /**
    * TODO: unused
    * Match userid with permission.
 *
    * @param userid usrid, most commonly mail
    * @param permission permission to be checked.
    * @return
    */
  def authorize(userid: String, permission: String): Future[Boolean] = {
    val userOpFuture: Future[Option[User]] = findByEmail(userid)
    userOpFuture.map { (userOp: Option[User]) =>
      userOp match {
        case Some(usr) => usr.permissions.contains(permission)
        case None => false
      }
    }
  }

  def updateUser(user: User): Future[Boolean]

  //
  def synchronizeRepos(): Unit

  def purgeMissing(): Unit

  def findById(id: String): Future[Option[User]]

  def findByEmail(email: String): Future[Option[User]]

  def adminUser: User = User(None, "admin.user", "admin@mail", Seq(SecurityRole.admin), Seq())
  def basicUser: User = User(None, "basic.user", "basic@mail", Seq(SecurityRole.basic), Seq())

  def debugUsers: Traversable[User]
}

/**
  * Class for managing and accessing Users.
  * Interface between Ldap and local user database.
  */
@Singleton
private class UserManagerImpl @Inject()(
    userRepo: UserRepo,
    ldapUserService: LdapUserService,
    connector: LdapConnector,
    ldapSettings: LdapSettings
  ) extends UserManager {

  override def debugUsers: Traversable[User] =
    if (ldapSettings.addDebugUsers)
      Seq(adminUser, basicUser)
    else
      Nil

  // add admin and basic users
  addUserIfNotPresent(adminUser)
  addUserIfNotPresent(basicUser)

  /**
    * Synchronize user entries of LDAP server and local database.
    * Users present in LDAP server but not present in database will be added.
    * Users present in LDAP server and database are synchronized by taking credentials from LDAP and keeping roles and permissions from local database.
    * TODO change to pass arbitrary user repo
    */
  override def synchronizeRepos(): Unit = {
    val ldapUsers = ldapUserService.getAll
    ldapUsers.map { ldapusr: LdapUser =>
      val foundFuture: Future[Traversable[User]] = userRepo.find(Seq("ldapDn" #== ldapusr.uid))
      foundFuture.map { found =>
        found.headOption match {
          case Some(usr) => userRepo.update(User(usr._id, ldapusr.name, ldapusr.email, usr.roles, usr.permissions))
          case None => userRepo.save(User(None, ldapusr.name, ldapusr.email, Seq(SecurityRole.basic), Seq()))
        }
      }
    }
  }

  /**
    * Removes users from local database which do not exist on the LDAP server.
    * Use this to clean the user data base or when moving from debug to production.
    * This will also remove all debug users!
    */
  override def purgeMissing(): Unit = {
    val localusersFuture: Future[Traversable[User]] = userRepo.find()
    val ldapusers: Traversable[LdapUser] = ldapUserService.getAll
    localusersFuture.map { localusers =>
      localusers.foreach { localusr =>
        val exists: Boolean = ldapusers.exists(ldapusr => ldapusr.uid == localusr.ldapDn)
        if (!exists) {
          userRepo.delete(localusr._id.get)
        }
      }
    }
  }

  /**
    * Authenticate user.
 *
    * @param id ID (e.g. mail) for matching.
    * @param password Password which should match the password associated to the mail.
    * @return None, if password is wrong or not associated mail was found.
    */
  override def authenticate(id: String, password: String): Future[Boolean] = {
    val dn = "uid=" + id + ",cn=users," + ldapSettings.dit

    // TODO: is exists needed?
    val exists = ldapUserService.getAll.find(_.uid == id).nonEmpty
    val auth = exists && connector.canBind(dn, password)
    Future(auth)
  }

  private def addUserIfNotPresent(user: User) = {
    userRepo.find(Seq("ldapDn" #== user.ldapDn)).map { users =>
      if (users.isEmpty)
        userRepo.save(user)
    }
  }


  /**
    * Given a mail, find the corresponding account.
    *
    * @param email mail to be matched.
    * @return Option containing Account with matching mail; None otherwise
    */
  override def findByEmail(email: String): Future[Option[User]] =
    userRepo.find(Seq("email" #== email)).map(_.headOption)

  /**
    * Given an id, find the corresponding account.
    *
    * @param id ID to be matched.
    * @return Option containing Account with matching ID; None otherwise
    */
  override def findById(id: String): Future[Option[User]] =
    userRepo.find(Seq("ldapDn" #== id)).map(_.headOption)

  /**
    * Update the user by looking up the username in the database and changing the other fields.
 *
    * @param user
    * @return
    */
  override def updateUser(user: User): Future[Boolean] =
    userRepo.update(user).map(_ => true)
}

