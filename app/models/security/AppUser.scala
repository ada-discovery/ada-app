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
    Scala.asJava(List(new SecurityRole("foo"),
                      new SecurityRole("bar")))
  }

  def getPermissions: java.util.List[AppUserPermission] = {
    Scala.asJava(List(new AppUserPermission("printers.edit")))
  }

  def getIdentifier: String = userName
}
