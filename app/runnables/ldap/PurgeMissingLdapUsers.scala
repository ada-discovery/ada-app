package runnables.ldap

import javax.inject.Inject

import models.security.UserManager
import runnables.GuiceBuilderRunnable

/**
  * Removes users from local database which do not exist on the LDAP server.
  * Use this to clean the user data base or when moving from debug to production.
  * This will also remove all debug users!
  */
class PurgeMissingLdapUsers @Inject() (userManager: UserManager) extends Runnable {

  override def run = {
    userManager.purgeMissing
    println("Removed local users which do not exist on LDAP server.")
  }
}

object PurgeMissingLdapUsers extends GuiceBuilderRunnable[PurgeMissingLdapUsers] with App { run }
