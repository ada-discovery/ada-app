package runnables.ldap

import javax.inject.Inject

import models.security.UserManager
import runnables.GuiceBuilderRunnable

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Synchronize user entries of LDAP server and local database.
  * Imports all users accessible to the LdapModule into the local database.
  * Imported users have no permissions and only "basic" role (they must be assigned manually).
  */
class ImportLdapUsers @Inject() (userManager: UserManager) extends Runnable {
  override def run() {
    Await.result(userManager.synchronizeRepos, 2 minutes)
    println("Local User database synchronized with LDAP users")
  }
}

object ImportLdapUsers extends GuiceBuilderRunnable[ImportLdapUsers] with App { run }
