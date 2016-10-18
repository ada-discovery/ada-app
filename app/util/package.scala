import org.apache.commons.lang.StringUtils
import play.twirl.api.Html

package object util {

//  def getDomainName(form: Form[_]) = form.get.getClass.getSimpleName.toLowerCase

  def matchesPath(coreUrl : String, url : String, matchPrefixDepth : Option[Int] = None) =
    matchPrefixDepth match {
      case Some(prefixDepth) => {
        var slashPos = 0
        for (i <- 1 to prefixDepth) {
          slashPos = coreUrl.indexOf('/', slashPos + 1)
        }
        if (slashPos == -1) {
          slashPos = coreUrl.indexOf('?')
          if (slashPos == -1)
            slashPos = coreUrl.length
        }
        val subString = coreUrl.substring(0, slashPos)
        url.startsWith(coreUrl.substring(0, slashPos))
      }
      case None => url.startsWith(coreUrl)
    }

  def getParamValue(url : String, param: String): Option[String] = {
    val tokens = url.split(param + "=")
    if (tokens.isDefinedAt(1))
      Some(tokens(1).split("&")(0))
    else
      None
  }

  def shorten(string : String, length: Int = 25) =
    if (string.length > length) string.substring(0, length) + ".." else string

  /**
   * Helper function for conversion of input string to camel case.
   * Replaces underscores "_" with whitespace " " and turns the next character into uppercase.
   *
   * @param s Input string.
   * @return String converted to camel case.
   */
  def toHumanReadableCamel(s: String): String = {
    StringUtils.splitByCharacterTypeCamelCase(s.replaceAll("[_|\\.]", " ")).map(
      _.toLowerCase.capitalize
    ).mkString(" ")
//    val split = s.split("_")
//    split.map { x => x.capitalize}.mkString(" ")
  }

  def fieldLabel(fieldName : String) =
    toHumanReadableCamel(JsonUtil.unescapeKey(fieldName))

  def fieldLabel(fieldName : String, fieldLabelMap : Option[Map[String, String]]) = {
    val defaultLabel = toHumanReadableCamel(JsonUtil.unescapeKey(fieldName))
    fieldLabelMap.map(_.getOrElse(fieldName, defaultLabel)).getOrElse(defaultLabel)
  }

  def chartElementName(title: String) = title.hashCode + "Chart"

  // retyping of column items needed because play templates do not support generics
  def typeColumn[T](column: (Option[String], String, T => Any)): (Option[String], String, Any  => Html) =
    (column._1, column._2, {item: Any =>
      val value = column._3(item.asInstanceOf[T])
      if (value.isInstanceOf[Html])
        value.asInstanceOf[Html]
      else if (value == null)
        Html("")
      else
        Html(value.toString)
    })

  def typeColumns[T](columns: (Option[String], String, T => Any)*): Traversable[(Option[String], String, Any => Html)] =
    columns.map(typeColumn[T])

  def formatTimeElement(i: Option[Int], addDelimiter: Boolean, noneValue: String) =
    i.map( value => (
      if (value < 10)
        "0" + value
      else
        value.toString) + (
      if(addDelimiter)
        ":"
      else "")
    ).getOrElse(noneValue)
}