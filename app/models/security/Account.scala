package models.security

import be.objectify.deadbolt.core.models.Subject

case class Account(id: Int, email: String, password: String, name: String, role: SecurityRole)

object Account {

  // Dummy user profiles.
  // To be changed as soon as user database will be used.
  val basicAccount = Account(0, "basic@mail", "123456", "basic user", SecurityRoleCache.basicRole)
  val adminAccount  = Account(1, "admin@mail", "123456", "admin user", SecurityRoleCache.adminRole)
  var accountCache = List[Account](basicAccount, adminAccount)

  // syntactic sugar.
  def apply(email: String, password: String): Option[Account] = authenticate(email, password)

  /**
    * Matches email and password for authentification.
    * Returns an Account, if successful.
    *
    * @param email Mail for matching.
    * @param password Password which should match the password associated to the mail.
    * @return None, if password is wrong or not associated mail was found. Corresponding Account otherwise.
    */
  def authenticate(email: String, password: String): Option[Account] = {
    findByEmail(email) match {
      case Some(account) =>
        if(account.password == password)
          Some(account)
        else
          None
      case None => None
    }
  }

  /**
    * Given a mail, find the corresponding account.
    *
    * @param email mail to be matched.
    * @return Option containing Account with matching mail; None otherwise
    */
  def findByEmail(email: String): Option[Account] = {
    accountCache.find(acc => (acc.email == email))
  }

  /**
    * Given an id, find the corresponding account.
    *
    * @param id ID to be matched.
    * @return Option containing Account with matching ID; None otherwise
    */
  def findById(id: Int): Option[Account] = {
    accountCache.find(acc => (acc.id == id))
  }


  /**
    * Return a sequence with all cached Accounts.
    *
    * @return
    */
  def findAll(): Seq[Account] = {
    accountCache.toSeq
  }

  /**
    * Add new Account to cache of existing ones.
    * Use this for initialization (e.g. in conjunction with a database).
    *
    * @param account account to be added.
    */
  def add(account: Account) : Unit = {
    accountCache = account::accountCache
  }


  /**
    * TODO: give each Account a subject so it directly be accessed. This method should not be necessary!
    * Convert given Account into a deadbolt Subject.
    *
    * @param acc Account to be converted.
    * @return Subject resulting from conversion.
    */
  def toSubject(acc : Account) : Subject = {
    val name = acc.role.getName
    new CustomUser(name, List(), List())
  }

}
