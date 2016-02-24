package models.security

import be.objectify.deadbolt.core.models.Subject
import models.{BSONObjectIdentity, User}
import persistence.RepoDef

import persistence.RepoTypeRegistry.UserRepo
import play.api.libs.json.{JsString, JsObject, Json}
import reactivemongo.bson.BSONObjectID


import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

/**
  * Class for managing and accessing Users.
  */
object UserManager {

  // TODO: move UserManager functionality directly into specialized UserRepo class
  val userRepo: UserRepo = RepoDef.UserRepo.repo

  // TODO: dummy user profiles. eventually remove them.
  val adminUser = new CustomUser("admin", "user", "admin@mail", "123456", List(SecurityRoleCache.adminRole), SecurityPermissionCache.adminPermissions)
  val basicUser = new CustomUser("basic", "user", "basic@mail", "123456", List(SecurityRoleCache.basicRole), SecurityPermissionCache.basicPermissions)

  // user cache
  lazy val userList: List[CustomUser] = {
    val timeout = 3600 millis
    val userFutureTrav: Future[Traversable[User]] = userRepo.find(None, None, None, None, None)
    val userTrav: Traversable[User] = Await.result(userFutureTrav, timeout)
    val temp: List[CustomUser] = userTrav.map{usr: User => convert(usr)}.toList
    basicUser::adminUser::temp
  }

  /**
    * Syntactic sugar. Calls "authenticate".
    */
  def apply(email: String, password: String): Option[Subject] = authenticate(email, password)

  /**
    * Matches email and password for authentification.
    * Returns an Account, if successful.
    *
    * @param email Mail for matching.
    * @param password Password which should match the password associated to the mail.
    * @return None, if password is wrong or not associated mail was found. Corresponding Account otherwise.
    */
  def authenticate(email: String, password: String): Option[CustomUser] = {
    findByEmail(email) match {
      case Some(account) =>
        if(account.getPassword == password)
          Some(account)
        else
          None
      case None => None
    }
  }



  // TODO temporary -> remove!
  def convert(user: User): CustomUser = {
    new CustomUser(user.userName, "", user.email, user.password)
  }



  /**
    * Given a mail, find the corresponding account.
    *
    * @param email mail to be matched.
    * @return Option containing Account with matching mail; None otherwise
    */
  def findByEmail(email: String): Option[CustomUser] = {
    userList.find((usr: CustomUser) => (usr.getMail == email))
  }

  /**
    * Given an id, find the corresponding account.
    *
    * @param id ID to be matched.
    * @return Option containing Account with matching ID; None otherwise
    */
  def findById(id: String): Option[CustomUser] = {
    userList.find((acc: CustomUser) => (acc.getIdentifier == id))
  }

  /**
    * Return a sequence with all cached Users.
    *
    * @return
    */
  def findAll(): Seq[CustomUser] = {
    userList.toSeq
  }
}
