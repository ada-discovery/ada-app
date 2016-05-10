package runnables.ldap

import javax.inject.Inject

import ldap.LdapUserRepo
import runnables.GuiceBuilderRunnable


/**
  * Manually launch LDAP cache update.
  * @param ldaprepo
  */
class LdapCacheUpdate @Inject() (ldaprepo: LdapUserRepo) extends Runnable {
  override def run() {
    //adaLdapUserServer.updateCache(true)
    ldaprepo.getCache(true)
    println("LDAP cache manually updated")
  }
}

object LdapCacheUpdate extends GuiceBuilderRunnable[LdapCacheUpdate] with App { run }