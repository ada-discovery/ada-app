package runnables

import javax.inject.Inject
import ldap.UserCache


/**
  * Manually launch LDAP cache update.
  * @param adaLdapUserServer
  */
class LdapCacheUpdate @Inject() (adaLdapUserServer: UserCache) extends Runnable {
  override def run() {
    adaLdapUserServer.updateCache(true)
    println("LDAP cache manually updated")
  }
}

object LdapCacheUpdate extends GuiceBuilderRunnable[LdapCacheUpdate] with App { run }