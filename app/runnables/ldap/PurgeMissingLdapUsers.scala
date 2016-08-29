package runnables.ldap

import javax.inject.Inject

import models.security.UserManager
import runnables.GuiceBuilderRunnable
import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Removes users from local database which do not exist on the LDAP server.
  * Use this to clean the user data base or when moving from debug to production.
  * This will also remove all debug users!
  */
class PurgeMissingLdapUsers @Inject() (userManager: UserManager) extends Runnable {

  override def run = {
    Await.result(userManager.purgeMissing, 2 minutes)
    println("Removed local users which do not exist on LDAP server.")
  }
}

object PurgeMissingLdapUsers extends GuiceBuilderRunnable[PurgeMissingLdapUsers] with App { run }
