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

  def jsonObjectsToCsv(
    delimiter : String,
    newLine : String = "\n",
    replacements : Iterable[(String, String)]
  )(items : Traversable[JsObject]) = {
    val sb = new StringBuilder(10000)

    val replaceAllAux = replaceAll(replacements)_

    if (!items.isEmpty) {
      val header = items.head.fields.map{ case (field, value) => decodeMongoKey(replaceAllAux(field))}.mkString(delimiter)
      sb.append(header + newLine)

      items.foreach { item =>
        val row = item.fields.map { case (field, value) =>
          value match {
            case JsNull => ""
            case _: JsString => replaceAllAux(value.as[String])
            case _ => value.toString()
          }
        }.mkString(delimiter)
        sb.append(row + newLine)
      }
    }
    sb.toString
  }

  private def replaceAll(replacements : Iterable[(String, String)])(value : String) =
    replacements.foldLeft(value) { case (string, (from , to)) => string.replaceAll(from, to) }
}