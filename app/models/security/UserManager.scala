package models.security

import javax.inject.{Singleton, Inject}

import dataaccess.Criterion
import Criterion.Infix
import com.google.inject.ImplementedBy
import ldap.{LdapSettings, LdapUserService, LdapConnector}
import dataaccess.RepoTypes.UserRepo

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.concurrent.Future.sequence
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

  def updateUser(user: User): Future[Boolean]

  def synchronizeRepos: Future[Unit]

  def purgeMissing: Future[Unit]

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
    if (ldapSettings.addDebugUsers) {
      // add admin and basic users
      addUserIfNotPresent(adminUser)
      addUserIfNotPresent(basicUser)

      Seq(adminUser, basicUser)
    } else
      Nil

  /**
    * Synchronize user entries of LDAP server and local database.
    * Users present in LDAP server but not present in database will be added.
    * Users present in LDAP server and database are synchronized by taking credentials from LDAP and keeping roles and permissions from local database.
    * TODO change to pass arbitrary user repo
    */
  override def synchronizeRepos: Future[Unit] = {
    val futures = ldapUserService.getAll.map { ldapUser: LdapUser =>
      for {
        found <- userRepo.find(Seq("ldapDn" #== ldapUser.uid)).map(_.headOption)
        _ <- found match {
          case Some(usr) =>
            userRepo.update(usr.copy(ldapDn = ldapUser.uid, email = ldapUser.email))
          case None =>
            userRepo.save(User(None, ldapUser.uid, ldapUser.email, Seq(SecurityRole.basic), Seq()))
        }
      } yield
        ()
    }
    sequence(futures).map(_ => ())
  }

  /**
    * Removes users from local database which do not exist on the LDAP server.
    * Use this to clean the user data base or when moving from debug to production.
    * This will also remove all debug users!
    */
  override def purgeMissing: Future[Unit] = {
    val ldapUserUids = ldapUserService.getAll.map(_.uid).toSet
    for {
      localUsers <- userRepo.find()
      _ <- {
        val nonMatchingLocalUsers = localUsers.filterNot( user =>
          ldapUserUids.contains(user.ldapDn)
        )
        sequence(
          nonMatchingLocalUsers.map( user =>
            userRepo.delete(user._id.get)
          )
        )
      }
    } yield
      ()
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

  private def addUserIfNotPresent(user: User) =
    userRepo.find(Seq("ldapDn" #== user.ldapDn)).map { users =>
      if (users.isEmpty)
        userRepo.save(user)
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

