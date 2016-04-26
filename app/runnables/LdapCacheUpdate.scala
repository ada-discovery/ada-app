package runnables

import javax.inject.Inject
import ldap.AdaLdapUserServer


/**
  * Manually launch LDAP cache update.
  * @param adaLdapUserServer
  */
class LdapCacheUpdate @Inject() (adaLdapUserServer: AdaLdapUserServer) extends Runnable {
  override def run() {
    adaLdapUserServer.updateCache(true)
    println("LDAP cache manually updated")
  }
}

object LdapCacheUpdate extends GuiceBuilderRunnable[LdapCacheUpdate] with App { run }