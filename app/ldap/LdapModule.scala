package ldap

import javax.inject.Inject

import com.google.inject.TypeLiteral
import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.name.Names
//import ldap.LdapRepoDef.Repo
import models._
import models.workspace.UserGroup
import models.security.CustomUser
import net.codingwell.scalaguice.ScalaModule

import play.api.Configuration
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}


import ldap.LdapRepo


object RepoTypes {
  type LdapUserRepo = LdapRepo[CustomUser]
  //type LdapGroupRepo = LdapRepo[UserGroup]
}


/*object LdapRepoDef extends Enumeration {
  case class Repo[T : Manifest](
       repo : T,
       named : Boolean = false
     ) extends super.Val {
      val man: Manifest[T] = manifest[T]
  }
  implicit def valueToRepo[T](x: Value) = x.asInstanceOf[Repo[T]]

  val UserRepo = Repo[RepoTypes.LdapUserRepo](
    new LdapRepo[CustomUser]("", LdapUtil.entryToUser, null))

  //val GroupRepo = Repo[RepoTypes.LdapGroupRepo](
  //  new LdapRepo[UserGroup]("", LdapUtil.entryToUserGroup))
}*/


class LdapModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    //bind[TemplateFailureListener].to[AdaTemplateFailureListener],
    //bind[HandlerCache].to[CustomHandlerCacheImpl],
    //bind[DeadboltExecutionContextProvider].to[AdaDeadboltExecutionContextProvider]
    //bind[]
  )
}


/*class LdapModule @Inject()(configuration: Configuration, ldapConnector: LdapConnector) extends ScalaModule {

  val dit: String = configuration.getString("ldap.dit").getOrElse("dc=ncer")
  val userRDN: String = "cn=users," + dit
  val groupRDN: String = "cn=groups," + dit


  def configure = {
    // bind repos defined above
    //LdapRepoDef.values.foreach(bindRepo(_))


    //bindRepo(Repo[RepoTypes.LdapUserRepo](
    //  new LdapRepo[CustomUser](userRDN, LdapUtil.entryToUser, ldapConnector)))


    //bindRepo(Repo[RepoTypes.LdapGroupRepo](
    //  new LdapRepo[UserGroup]("dit", LdapUtil.entryToUserGroup, ldapConnector)))

  }

  private def bindRepo[T](repo : LdapRepoDef.Repo[T]) = {
    implicit val manifest = repo.man
    if (repo.named)
      bind[T]
        .annotatedWith(Names.named(repo.toString))
        .toInstance(repo.repo)
    else
      bind[T].toInstance(repo.repo)
  }
}*/