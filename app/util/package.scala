import play.api.data.Form
import play.api.libs.json.{JsObject, JsString, JsNull}

package object util {

//  def getDomainName(form: Form[_]) = form.get.getClass.getSimpleName.toLowerCase

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

  def shorten(string : String) =
    if (string.length > 25) {string.substring(0, 23) + ".."} else string

  /**
   * Helper function for conversion of input string to camel case.
   * Replaces underscores "_" with whitespace " " and turns the next character into uppercase.
   *
   * @param s Input string.
   * @return String converted to camel case.
   */
  def toCamel(s: String): String = {
    val split = s.split("_")
    split.map { x => x.capitalize}.mkString(" ")
  }

  def fieldLabel(fieldName : String, fieldLabelMap : Option[Map[String, String]]) =
    fieldLabelMap.map(_.getOrElse(fieldName, toCamel(fieldName))).getOrElse(toCamel(fieldName))

  def chartElementName(title: String) = title.hashCode + "Chart"
}