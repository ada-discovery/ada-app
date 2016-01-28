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

  def shorten(string : String) =
    if (string.length > 20) {string.substring(0, 18) + ".."} else string
}