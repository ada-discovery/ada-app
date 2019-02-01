package models.security

import javax.inject.{Singleton, Inject}

import models.User
import com.google.inject.ImplementedBy
import ldap.{LdapSettings, LdapService}
import dataaccess.RepoTypes.UserRepo
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.security.SecurityRole

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.concurrent.Future.sequence

@ImplementedBy(classOf[UserManagerImpl])
trait UserManager {

  /**
    * Matches email and password for authentication.
    * Returns an Account, if successful.
 *
    * @param id ID (e.g. mail) for matching.
    * @param password Password which should match the password associated to the mail.
    * @return None, if password is wrong or not associated mail was found. Corresponding Account otherwise.
    */
  def authenticate(id: String, password: String): Future[Boolean] =
    findByEmail(id).map(userOp =>
      userOp match {
        case Some(usr) => true //(usr.password == password)
        case None => false
      }
    )

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
    ldapService: LdapService,
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
    val futures = ldapService.listUsers.map { ldapUser: LdapUser =>
      for {
        found <- userRepo.find(Seq("ldapDn" #== ldapUser.uid)).map(_.headOption)
        _ <- found match {
          case Some(usr) =>
            userRepo.update(usr.copy(ldapDn = ldapUser.uid, email = ldapUser.email))
          case None =>
            userRepo.save(User(None, ldapUser.uid, ldapUser.email, Seq(), Seq()))
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
  override def purgeMissing: Future[Unit] =
    for {
      // local users
      localUsers <- userRepo.find()

      // retrieve all LDAP users and remove those who are not matched
      _ <- {
        val ldapUserUids = ldapService.listUsers.map(_.uid).toSet
        val nonMatchingLocalUsers = localUsers.filterNot(user => ldapUserUids.contains(user.ldapDn))
        val nonMatchingIds = nonMatchingLocalUsers.map(_._id.get)

        userRepo.delete(nonMatchingIds)
      }
    } yield
      ()

  /**
    * Authenticate user.
    *
    * @param id ID (e.g. mail) for matching.
    * @param password Password which should match the password associated to the mail.
    * @return None, if password is wrong or not associated mail was found.
    */
  override def authenticate(id: String, password: String): Future[Boolean] =
    Future {
//      val exists = ldapUserService.getAll.find(_.uid == id).nonEmpty
      ldapService.canBind(id, password)
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

