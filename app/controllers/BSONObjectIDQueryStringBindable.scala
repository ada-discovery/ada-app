package controllers

import play.api.mvc.QueryStringBindable
import reactivemongo.bson.BSONObjectID

object BSONObjectIDQueryStringBindable extends QueryStringBindable[BSONObjectID] {

  private val stringBinder = implicitly[QueryStringBindable[String]]

  override def bind(
      key: String,
      params: Map[String, Seq[String]]
    ): Option[Either[String, BSONObjectID]] = {
      for {
        leftRightString <- stringBinder.bind(key, params)
      } yield {
        leftRightString match {
          case Right(string) => try {
            Right(BSONObjectID(string))
          } catch {
            case e: IllegalArgumentException => Left("Unable to bind BSON Object Id from String to " + key)
          }
          case _ => Left("Unable to bind BSON Object Id from String to " + key)
        }
      }
    }

    override def unbind(key: String, id: BSONObjectID): String =
      stringBinder.unbind(key, id.stringify)
}
