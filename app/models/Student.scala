package models

import play.api.libs.json.Json
import play.modules.reactivemongo.json.BSONFormats._

//case class Student(_id : Option[String], name: String, age: Int)
case class Student(name: String, age: Int)

object Student {
  implicit val StudentFormat = Json.format[Student]
}