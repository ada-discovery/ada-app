import models._
import org.apache.commons.lang.StringUtils
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import play.api.{Logger, LoggerLike}
import play.api.mvc.{AnyContent, Request}
import play.twirl.api.Html

import scala.collection.TraversableLike
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

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

//  def sparkParallelize[T: ClassTag, U](
//    sparkContext: SparkContext,
//    items: Seq[T])(
//    fun: T => Future[U]
//  ): Future[Seq[U]] = {
//    sparkContext.parallelize(items).map( item =>
//      fun(item).map(Seq(_))
//    ).reduce { case (fut1, fut2) =>
//      for {
//        seq <- fut1
//        res <- fut2
//      } yield
//        seq ++ res
//    }
//  }

  def retry[T](failureMessage: String, logger: LoggerLike, maxAttemptNum: Int)(f: => Future[T]): Future[T] = {
    def retryAux(attempt: Int): Future[T] =
      f.recoverWith {
        case e: Exception => {
          if (attempt < maxAttemptNum) {
            logger.warn(s"${failureMessage}. ${e.getMessage}. Attempt ${attempt}. Retrying...")
            retryAux(attempt + 1)
          } else
            throw e
        }
      }

    retryAux(1)
  }

  implicit class GroupMapList[A, B](list: Traversable[(A, B)]) {
    def toGroupMap: Map[A, Traversable[B]] =
      list.groupBy(_._1).map(x => (x._1, x._2.map(_._2)))
  }
}