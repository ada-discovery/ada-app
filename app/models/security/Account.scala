package jp.t2v.lab.play2.auth.sample

// TODO rename package e.g. to "security.authentification"



case class Account(id: Int, email: String, password: String, name: String, role: Role)

object Account {

  // dummy normal user
  val accountNormal = Account(0, "normal@mail", "123456", "normal name", Role.NormalUser)
  // dummy admin user
  val accountAdmin = Account(1, "admin@mail", "123456", "admin user", Role.Administrator)


  //def apply(a: SyntaxProvider[Account])(rs: WrappedResultSet): Account = accountAdmin
  //def apply(): Account = accountAdmin
  def apply(email: String, password: String): Account = Account(0, email, password, "admin user", Role.Administrator)
  //def apply(id: Int, email: String, password: String, name: String, role: Role): Account = Account(id, email, password, name, role)


  // TODO: mockup; change this
  def authenticate(email: String, password: String): Option[Account] = {
    //return dummy account
    //Some(accountAdmin)
    //None
    email match {
      case accountNormal.email => if(password == accountNormal.password) Some(accountNormal) else None
      case accountAdmin.email  => if(password == accountAdmin.password)  Some(accountAdmin)  else None
      case _ => None
    }
  }


  // use this, once we have user data base
  def findByEmail(email: String): Option[Account] = {
    None
  }

  // TODO mockup change later
  def findById(id: Int): Option[Account] = {
    id match {
      case 0 => Some(accountAdmin)
      case 1 => Some(accountNormal)
      case _ => None
    }

  }

  // TODO expand ot return dynamically built list of all users
  def findAll(): Seq[Account] = {
    List(accountAdmin, accountNormal).toSeq
  }

  // TODO add new account to existing ones
  def create(account: Account) : Unit = {
    Unit
  }

}
