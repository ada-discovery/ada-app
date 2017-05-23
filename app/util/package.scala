import models._
import org.apache.commons.lang.StringUtils
import play.api.mvc.{AnyContent, Request}
import play.twirl.api.Html

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

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

  def widgetElementId(chart: Widget) = chart._id.stringify + "Widget"

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

  def getRequestParamMap(implicit request: Request[AnyContent]): Map[String, Seq[String]] = {
    val body = request.body
    if (body.asFormUrlEncoded.isDefined)
      body.asFormUrlEncoded.get
    else if (body.asMultipartFormData.isDefined)
      body.asMultipartFormData.get.asFormUrlEncoded
    else
      throw new AdaException("FormUrlEncoded or MultipartFormData request expected.")
  }

  def getRequestParamValue(paramKey: String)(implicit request: Request[AnyContent]) =
    getRequestParamMap.get(paramKey).get.head

  def enumToValueString(enum: Enumeration): Seq[(String, String)] =
    enum.values.toSeq.sortBy(_.id).map(value => (value.toString, toHumanReadableCamel(value.toString)))

  def toChartData(widget: CategoricalCountWidget) =
    widget.data.map { case (name, series) =>
      val sum = if (widget.isCumulative) series.map(_.count).max else series.map(_.count).sum
      val data = series.map { case Count(label, count, key) =>
        (shorten(label), if (widget.useRelativeValues) 100 * count.toDouble / sum else count, key)
      }
      (name, data)
    }

  def toChartData(widget: NumericalCountWidget[_]) = {
    def numericValue(x: Any) =
      x match {
        case x: java.util.Date => x.getTime.toString
        case _ => x.toString
      }

    widget.data.map { case (name, series) =>
      val sum = if (widget.isCumulative) series.map(_.count).max else series.map(_.count).sum
      val data = series.map { case Count(value, count, _) =>
        (numericValue(value), if (widget.useRelativeValues) 100 * count.toDouble / sum else count)
      }
      (name, data)
    }
  }

  def seqFutures[T, U](
    items: TraversableOnce[T])(
    fun: T => Future[U]
  ): Future[Seq[U]] = {
    items.foldLeft(Future.successful[List[U]](Nil)) {
      (f, item) => f.flatMap {
        x => fun(item).map(_ :: x)
      }
    } map (_.reverse)
  }
}