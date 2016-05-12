package runnables.ldap

import javax.inject.Inject

import ldap.LdapUserRepo
import models.security.{UserManager, CustomUser, LdapUser}
import play.api.libs.json.JsObject
import runnables.GuiceBuilderRunnable
import persistence.RepoTypes.UserRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/**
  * Synchronize user entries of LDAP server and local database.
  * Imports all users accessible to the LdapModule into the local database.
  * Imported users have no permissions and only "basic" role (they must be assigned manually).
  */
class ImportLdapUsers @Inject() (userManager: UserManager) extends Runnable {
  override def run() {
    userManager.synchronizeRepos
    println("Local User database synchronized with LDAP users")
  }
}

object ImportLdapUsers extends GuiceBuilderRunnable[ImportLdapUsers] with App { run }
