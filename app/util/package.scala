import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import models._
import org.apache.commons.lang.StringUtils
import play.api.{Logger, LoggerLike}
import dataaccess.JsonUtil
import org.apache.commons.io.IOUtils
import org.incal.play.util.WebUtil
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{AnyContent, Call, Request, Result}
import play.twirl.api.Html
import play.api.mvc.Results.Redirect

import scala.collection.Iterator.empty
import scala.collection.{AbstractIterator, Iterator, Traversable}
import scala.concurrent.{ExecutionContext, Future}

package object util {

  def shorten(string : String, length: Int = 25) =
    if (string.length > length) string.substring(0, length - 2) + ".." else string

  /**
   * Helper function for conversion of input string to camel case.
   * Replaces underscores "_" with whitespace " " and turns the next character into uppercase.
   *
   * @param s Input string.
   * @return String converted to camel case.
   */
  def toHumanReadableCamel(s: String): String = {
    StringUtils.splitByCharacterTypeCamelCase(s.replaceAll("[_|\\.]", " ")).filter(!_.equals(" ")).map(
      _.toLowerCase.capitalize
    ).mkString(" ")
//    val split = s.split("_")
//    split.map { x => x.capitalize}.mkString(" ")
  }

  def fieldLabel(fieldName : String): String =
    toHumanReadableCamel(JsonUtil.unescapeKey(fieldName))

  def fieldLabel(fieldName : String, fieldLabelMap : Option[Map[String, String]]) = {
    val defaultLabel = toHumanReadableCamel(JsonUtil.unescapeKey(fieldName))
    fieldLabelMap.map(_.getOrElse(fieldName, defaultLabel)).getOrElse(defaultLabel)
  }

  def fieldLabel(field: Field): String =
    field.label.getOrElse(fieldLabel(field.name))

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

  def toChartData(widget: ScatterWidget[_, _]) = {
    def numericValue(x: Any) =
      x match {
        case x: java.util.Date => x.getTime.toString
        case _ => x.toString
      }

    widget.data.map { case (name, points) =>
      var numPoints = points.map { case (point1, point2) => (numericValue(point1), numericValue(point2)) }
      (name, numPoints)
    }
  }

  def toChartData(widget: ValueScatterWidget[_, _, _]) = {
    def numericValue(x: Any) =
      x match {
        case x: java.util.Date => x.getTime.toString
        case _ => x.toString
      }

    widget.data.map { case (x, y, z) =>
      (numericValue(x), numericValue(y), numericValue(z))
    }
  }

  def seqFutures[T, U](
    items: TraversableOnce[T])(
    fun: T => Future[U]
  ): Future[Seq[U]] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    items.foldLeft(Future.successful[List[U]](Nil)) {
      (f, item) => f.flatMap {
        x => fun(item).map(_ :: x)
      }
    } map (_.reverse)
  }

//  def parallelize[T](
//    futures: Traversable[Future[T]],
//    threadsNum: Int
//  ): Future[Traversable[T]] = {
//    println("Threads: " + threadsNum)
//    val threadPool = Executors.newFixedThreadPool(threadsNum)
//    implicit val ec = ExecutionContext.fromExecutor(threadPool)
//
////    val newFutures = futures.map( future =>
////      future.transform(identity, identity)
//////      Future.successful(()).flatMap(_ => future)
////    )
//
//    val resultFuture = Future.sequence(futures)
//
//    threadPool.shutdown()
//    resultFuture
//  }

  def parallelize[T, U](
    inputs: Traversable[T],
    threadsNum: Int)(
    fun: T => U
  ): Future[Traversable[U]] = {
    val threadPool = Executors.newFixedThreadPool(threadsNum)
    implicit val ec = ExecutionContext.fromExecutor(threadPool)

    val futures = inputs.map(input => Future { fun(input) })
    val resultFuture = Future.sequence(futures)

    resultFuture.map { results =>
      threadPool.shutdown()
      results
    }
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
    import scala.concurrent.ExecutionContext.Implicits.global

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

  implicit class GroupMapList3[A, B, C](list: Traversable[(A, B, C)]) {

    def toGroupMap: Map[A, Traversable[(B, C)]] =
      list.groupBy(_._1).map(x =>
        (x._1, x._2.map(list => (list._2, list._3)))
      )
  }

  type STuple3[T] = (T, T, T)

  def crossProduct[T](list: Traversable[Traversable[T]]): Traversable[Traversable[T]] =
    list match {
      case Nil => Nil
      case xs :: Nil => xs map (Traversable(_))
      case x :: xs => for {
        i <- x
        j <- crossProduct(xs)
      } yield Traversable(i) ++ j
    }

  def getListOfFiles(dir: String): Seq[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory)
      d.listFiles.filter(_.isFile).toList
    else
      Nil
  }

  implicit class GrouppedVariousSize[A](list: Traversable[A]) {

    def grouped(sizes: Traversable[Int]): Iterator[Seq[A]] = new AbstractIterator[Seq[A]] {
      private var hd: Seq[A] = _
      private var hdDefined: Boolean = false
      private val listIt = list.toIterator
      private val groupSizesIt = sizes.toIterator

      def hasNext = hdDefined || listIt.hasNext && groupSizesIt.hasNext && {
        val groupSize = groupSizesIt.next()
        hd = for (_ <- 1 to groupSize if listIt.hasNext) yield listIt.next()
        hdDefined = true
        true
      }

      def next() = if (hasNext) {
        hdDefined = false
        hd
      } else
        empty.next()
    }
  }

  def writeStringAsStream(string: String, file: File) = {
    val outputStream = Stream(string.getBytes(StandardCharsets.UTF_8))
    writeByteArrayStream(outputStream, file)
  }

  def writeByteArrayStream(data: Stream[Array[Byte]], file : File) = {
    val target = new BufferedOutputStream(new FileOutputStream(file))
    try
      data.foreach(IOUtils.write(_, target))
    finally
      target.close
  }

  def writeByteStream(data: Stream[Byte], file : File) = {
    val target = new BufferedOutputStream(new FileOutputStream(file))
    try data.foreach(target.write(_)) finally target.close
  }

  def toJsonHtml[T](o: T)(implicit tjs: Writes[T]): Html =
    Html(Json.stringify(Json.toJson(o)))

  def nonAlphanumericToUnderscore(string: String) = string.replaceAll("[^\\p{Alnum}]", "_")
}