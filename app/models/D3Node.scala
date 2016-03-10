package models

import play.api.libs.json.Json

case class D3Node(name: String, size: Option[Int] = None, var children: Seq[D3Node] = Seq())

object D3Node {
  implicit val d3NodeFormat = Json.format[D3Node]
}