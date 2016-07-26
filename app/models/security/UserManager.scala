package models.security

import javax.inject.{Singleton, Inject}

import models.Criterion.CriterionInfix
import com.google.inject.ImplementedBy
import ldap.{LdapUserRepo, LdapConnector}
import persistence.AsyncReadonlyRepo
import persistence.RepoTypes.UserRepo
import play.api.libs.json.{JsString, JsValue, JsObject, Json}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

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
    val userOpFuture: Future[Option[CustomUser]] = findByEmail(userid)
    userOpFuture.map { (userOp: Option[CustomUser]) =>
      userOp match {
        case Some(usr) => usr.permissions.contains(permission)
        case None => false
      }
    }
  }

  def updateUser(user: CustomUser): Future[Boolean]

  //
  def synchronizeRepos(): Unit

  def purgeMissing(): Unit

  def findById(id: String): Future[Option[CustomUser]]

  def findByEmail(email: String): Future[Option[CustomUser]]

  def adminUser: CustomUser = CustomUser(None, "admin.user", "admin@mail", Seq(SecurityRole.admin), Seq())
  def basicUser: CustomUser = CustomUser(None, "basic.user", "basic@mail", Seq(SecurityRole.basic), Seq())

  def debugUsers: Traversable[CustomUser]
}

/**
  * Class for managing and accessing Users.
  * Interface between Ldap and local user database.
  */
@Singleton
private class UserManagerImpl @Inject()(userRepo: UserRepo, ldapRepo: LdapUserRepo) extends UserManager {

  val connector = ldapRepo.connector

  override def debugUsers: Traversable[CustomUser] = {
    if(connector.ldapsettings.addDebugUsers)
      Traversable(adminUser, basicUser)
    else
      Traversable()
  }

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
    val ldapusers: Traversable[LdapUser] = ldapRepo.getCache(true)
    ldapusers.map { ldapusr: LdapUser =>
      val foundFuture: Future[Traversable[CustomUser]] = userRepo.find(Seq("ldapDn" #= ldapusr.getDN))
      foundFuture.map { found =>
        found.headOption match {
          case Some(usr) => userRepo.update(CustomUser(usr._id, ldapusr.getDN, ldapusr.email, usr.roles, usr.permissions))
          case None => userRepo.save(CustomUser(None, ldapusr.getDN, ldapusr.email, Seq(SecurityRole.basic), Seq()))
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
    val localusersFuture: Future[Traversable[CustomUser]] = userRepo.find()
    val ldapusers: Traversable[LdapUser] = ldapRepo.getCache(true)
    localusersFuture.map { localusers =>
      localusers.foreach { localusr =>
        val exists: Boolean = ldapusers.exists(ldapusr => ldapusr.getDN == localusr.ldapDn)
        if (!exists) {
          userRepo.delete(localusr._id.get)
        }
      }
    }
  }

  /**
    * Authenticate user and add user to database, if it does not exist.
 *
    * @param id ID (e.g. mail) for matching.
    * @param password Password which should match the password associated to the mail.
    * @return None, if password is wrong or not associated mail was found. Corresponding Account otherwise.
    */
  override def authenticate(id: String, password: String): Future[Boolean] = {
    val dn = "uid=" + id + ",cn=users," + connector.ldapsettings.dit

    val existsFuture: Future[Traversable[LdapUser]] = ldapRepo.find(Seq("_id" #= id))
    val auth = existsFuture.map{ exists: Traversable[LdapUser] =>
      !exists.isEmpty && connector.canBind(dn, password)
    }

    auth

    /*val auth = connector.canBind(dn, password)
    if (auth) {
      val usr = new CustomUser(None, id, "", Seq(), Seq())
      addUserIfNotPresent(usr)
    }
    Future(auth)*/
  }


  private def addUserIfNotPresent(user: CustomUser) = {
    userRepo.find(Seq("ldapDn" #= user.ldapDn)).map { users =>
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
  override def findByEmail(email: String): Future[Option[CustomUser]] = {
    val usersFuture = userRepo.find(Seq("email" #= email))
    usersFuture.map { users =>
      users.headOption
    }
  }

  /**
    * Given an id, find the corresponding account.
    *
    * @param id ID to be matched.
    * @return Option containing Account with matching ID; None otherwise
    */
  override def findById(id: String): Future[Option[CustomUser]] = {
    val usersFuture = userRepo.find(Seq("ldapDn" #= id))
    usersFuture.map { users =>
      users.headOption
    }
  }

  /**
    * Update the user by looking up the username in the database and changing the other fields.
 *
    * @param user
    * @return
    */
  override def updateUser(user: CustomUser): Future[Boolean] = {
    userRepo.update(user)
    Future(true)
  }
}

