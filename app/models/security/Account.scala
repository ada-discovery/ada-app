package models.security

case class Account(id: Int, email: String, password: String, name: String, role: SecurityRole)

object Account {

  // dummy users
  val accountNormal = Account(0, "default@mail", "123456", "default name", new SecurityRole("default"))
  val accountAdmin  = Account(1, "admin@mail", "123456", "admin user", new SecurityRole("admin"))
  var accountList = List[Account](accountNormal, accountAdmin)

  def apply(email: String, password: String): Account = accountNormal

  // TODO: mockup; change this
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

  // TODO mockup, change later
  def findByEmail(email: String): Option[Account] = {
    email match {
      case accountNormal.email => Some(accountNormal)
      case accountAdmin.email => Some(accountAdmin)
      case _ => None
    }
  }

  // TODO mockup change later
  def findById(id: Int): Option[Account] = {
    id match {
      case 0 => Some(accountNormal)
      case 1 => Some(accountAdmin)
      case _ => None
    }

  }

  def findAll(): Seq[Account] = {
    accountList.toSeq
  }

  def add(account: Account) : Unit = {
    accountList = account::accountList
  }

}
