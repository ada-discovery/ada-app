package runnables

import org.fusesource.scalate._

object ScalateTest extends App {
  val engine = new TemplateEngine
  val output = engine.layout("bar.scaml", Map("name" -> ("Hiram", "Chirino"), "city" -> "Tampa"))
  println(output)
}
