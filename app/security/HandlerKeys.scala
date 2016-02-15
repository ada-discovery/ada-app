package security

import be.objectify.deadbolt.scala.HandlerKey

/**
  *
  *  Deadbolt handler key defintions
  *
  */
object HandlerKeys {

  val defaultHandler = Key("defaultHandler")              // key for default user
  val altHandler = Key("altHandler")                      // alternative handler; to be changed
  val userlessHandler = Key("userlessHandler")            // if no user logged in

  case class Key(name: String) extends HandlerKey
}
