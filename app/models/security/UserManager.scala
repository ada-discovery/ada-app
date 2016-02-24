package models.security

import be.objectify.deadbolt.core.models.Subject
import models.User
import persistence.RepoDef

import persistence.RepoTypeRegistry.UserRepo
import play.api.libs.json.{JsString, JsObject, Json}


import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

/**
  * Class for managing and accessing Users.
  */
object UserManager {

  // TODO: move UserManager functionality directly into specialized UserRepo class
  val userRepo: UserRepo = RepoDef.UserRepo.repo

  // TODO: dummy user profiles. eventually remove them.
  val adminUser = new AdminUser
  val basicUser = new BasicUser
  var userList = List[AbstractUser](basicUser, adminUser)

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
  def authenticate(email: String, password: String): Option[AbstractUser] = {
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
  def convert(user: User): AbstractUser = {
    new CustomUser(user.email, user.password, user.userName)
  }



  /**
    * Given a mail, find the corresponding account.
    *
    * @param email mail to be matched.
    * @return Option containing Account with matching mail; None otherwise
    */
  /*def findByEmail(email: String): Option[AbstractUser] = {
    userList.find((usr: AbstractUser) => (usr.getMail == email))
  }*/
  def findByEmail(email: String): Option[AbstractUser] = {
    val criteria: Option[JsObject] = None
    //val criteria: Option[JsObject] = Some(JsObject("conditions" -> JsObject("fieldName" -> JsString("email") :: ("conditionType" -> JsString("=")) :: ("condition" -> JsString(email)) :: Nil) :: Nil))
    val userFutureTrav: Future[Traversable[User]] = userRepo.find(criteria, None, None, None, None)

    val timeout = 3600 millis
    val userTrav: Traversable[User] = Await.result(userFutureTrav, timeout)
    val userOp = userTrav.find((usr: User) => (usr.email == email))

    if(userOp.isDefined){
      Some(convert(userOp.get))
    }else{
      // override for debugging
      userList.find((usr: AbstractUser) => (usr.getMail == email))
    }
  }

  /**
    * Given an id, find the corresponding account.
    *
    * @param id ID to be matched.
    * @return Option containing Account with matching ID; None otherwise
    */
  /*def findById(id: String): Option[AbstractUser] = {
    userList.find((acc: AbstractUser) => (acc.getIdentifier == id))
  }*/
  def findById(id: String): Option[AbstractUser] = {
    val criteria: Option[JsObject] = None
    val userFutureTrav: Future[Traversable[User]] = userRepo.find(criteria, None, None, None, None)

    val timeout = 3600 millis
    val userTrav: Traversable[User] = Await.result(userFutureTrav, timeout)
    val userOp = userTrav.find((usr: User) => (usr.userName == id))

    if(userOp.isDefined){
      Some(convert(userOp.get))
    }else{
      // override for debugging
      userList.find((usr: AbstractUser) => (usr.getIdentifier == id))
    }
  }


  /**
    * Return a sequence with all cached Users.
    *
    * @return
    */
  def findAll(): Seq[AbstractUser] = {
    userList.toSeq
  }

  /**
    * Add new AbstractUser to cache of existing ones.
    * Use this for initialization (e.g. in conjunction with a database).
    *
    * @param user User to be added.
    */
  def add(user: AbstractUser) : Unit = {
    userList = user::userList
  }
}
