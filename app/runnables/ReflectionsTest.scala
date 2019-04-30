package runnables

import org.ada.server.calc.json.JsonInputConverter

import scala.collection.JavaConversions._

object ReflectionsTest extends App {

  import org.reflections.Reflections

  val reflections = new Reflections("org.ada.server.calc ")

  println(reflections.getSubTypesOf(classOf[JsonInputConverter[_]]).mkString("\n"))
}