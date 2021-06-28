package org.ada.web.models

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}

case class BearerToken(accessToken: String,
                       expiresIn: Int,
                       refreshToken: Option[String],
                       refreshExpiresIn: Option[Int],
                       tokenType: Option[String],
                       notBeforePolicy: Option[Int],
                       scope: Option[String]
                      )

object BearerToken {

  implicit val read : Reads[BearerToken] = (
    (__ \ "access_token").read[String] and
      (__ \ "expires_in").read[Int] and
      (__ \ "refresh_token").readNullable[String] and
      (__ \ "refresh_expires_in").readNullable[Int] and
      (__ \ "token_type").readNullable[String] and
      (__ \ "not-before-policy").readNullable[Int] and
      (__ \ "scope").readNullable[String]
    )(BearerToken.apply _)

}