import play.api.data.Form
import play.api.libs.json.{JsObject, JsString, JsNull}

package object util {

  def getDomainName(form: Form[_]) = form.get.getClass.getSimpleName.toLowerCase

  def matchesPath(coreUrl : String, url : String, matchPrefixDepth : Option[Int] = None) =
    matchPrefixDepth match {
      case Some(prefixDepth) => {
        var slashPos = 0
        for (i <- 1 to prefixDepth) {
          slashPos = coreUrl.indexOf('/', slashPos + 1)
        }
        if (slashPos == -1)
          slashPos = coreUrl.length
        url.startsWith(coreUrl.substring(0, slashPos))
      }
      case None => coreUrl.equals(url)
    }

  def encodeMongoKey(key : String) =
    key.replaceAll("\\.", "\\u002e") // \u2024// replaceAll("\\", "\\\\").replaceAll("\\$", "\\u0024").

  def decodeMongoKey(key : String) =
    key.replaceAll("u002e", "\\.") // .replaceAll("\\u0024", "\\$").replaceAll("\\\\", "\\")

  def jsonObjectsToCsv(delimiter : String, newLine : String = "\n")(items : Iterable[JsObject]) = {
    val sb = new StringBuilder(10000)
    if (!items.isEmpty) {
      val header = items.head.fields.map(_._1).mkString(delimiter)
      sb.append(header + newLine)

      items.foreach { item =>
        val row = item.fields.map { case (field, value) =>
          value match {
            case JsNull => ""
            case _: JsString => value.as[String]
            case _ => value.toString()
          }
        }.mkString(delimiter)
        sb.append(row + newLine)
      }
    }
    sb.toString
  }
}