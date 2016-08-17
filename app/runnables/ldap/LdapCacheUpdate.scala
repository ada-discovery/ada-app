package runnables.ldap

import javax.inject.Inject

import runnables.GuiceBuilderRunnable

/**
  * Manually launch LDAP cache update.
  * @param ldaprepo
  */
class LdapCacheUpdate extends Runnable { //@Inject() (ldaprepo: LdapUserRepo)
  override def run() {
//    ldaprepo.getCache(true)
    println("LDAP user cache manually updated")
  }
}

object LdapCacheUpdate extends GuiceBuilderRunnable[LdapCacheUpdate] with App { run }