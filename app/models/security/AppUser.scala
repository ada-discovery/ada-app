package models.security

import play.libs.Scala
import be.objectify.deadbolt.core.models.Subject

/**
 *
 * @author Steve Chaloner (steve@objectify.be)
 */

class AppUser(val userName: String) extends Subject
{
  def getRoles: java.util.List[SecurityRole] = {
    Scala.asJava(List(new SecurityRole("default"),
                      new SecurityRole("admin")))
  }

  def getPermissions: java.util.List[AppUserPermission] = {
    Scala.asJava(List(new AppUserPermission("view.data.basic"),
                      new AppUserPermission("view.data.full"),
                      new AppUserPermission("view.admin")))
  }

  def getIdentifier: String = userName
}
