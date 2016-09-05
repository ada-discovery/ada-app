package util

import java.text.{ParseException, SimpleDateFormat}

import play.api.libs.json._
import play.api.mvc.QueryStringBindable
import java.util.Date

object JsonUtil {

  private val dateFormats = Seq(
    "yyyy-MM-dd",
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd HH:mm:ss",
    "dd-MMM-yyyy HH:mm:ss",
    "dd.MM.yyyy",
    "MM.yyyy"
  ).map(new SimpleDateFormat(_))

  private val standardDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  def createQueryStringBinder[E:Format](implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[E] {

    override def bind(
      key: String,
      params: Map[String, Seq[String]]
    ): Option[Either[String, E]] = {
      for {
        jsonString <- stringBinder.bind(key, params)
      } yield {
        jsonString match {
          case Right(jsonString) => {
            val filterJson = Json.parse(jsonString)
            Right(filterJson.as[E])
          }
          case _ => Left("Unable to bind from JSON to " + key)
        }
      }
    }

    override def unbind(key: String, filterSpec: E): String =
      stringBinder.unbind(key, Json.stringify(Json.toJson(filterSpec)))
  }

  def escapeKey(key : String) =
    key.replaceAll("\\.", "\\u002e") // \u2024// replaceAll("\\", "\\\\").replaceAll("\\$", "\\u0024").

  def unescapeKey(key : String) =
    key.replaceAll("u002e", "\\.") // .replaceAll("\\u0024", "\\$").replaceAll("\\\\", "\\")

  def jsonObjectsToCsv(
    delimiter : String,
    eol: String = "\n",
    fieldNames: Option[Seq[String]] = None,
    replacements : Traversable[(String, String)]
  )(items : Traversable[JsObject]) = {
    val sb = new StringBuilder(10000)

    val replaceAllAux = replaceAll(replacements)_

    if (!items.isEmpty) {
      val headerFieldNames = fieldNames.getOrElse(items.head.fields.map(_._1))
      val header = headerFieldNames.map(fieldName =>
        unescapeKey(replaceAllAux(fieldName))
      ).mkString(delimiter)

      sb.append(header + eol)

      items.foreach { item =>
        val itemFieldNameValueMap = item.fields.toMap
        val row = headerFieldNames.map{ fieldName =>
          itemFieldNameValueMap.get(fieldName).fold("")
            { value =>
              value match {
                case JsNull => ""
                case _: JsString => replaceAllAux(value.as[String])
                case _ => value.toString()
              }
            }
        }.mkString(delimiter)
        sb.append(row + eol)
      }
    }
    sb.toString
  }

  private def replaceAll(
    replacements: Traversable[(String, String)])(
    value : String
  ) =
    replacements.foldLeft(value) { case (string, (from , to)) => string.replaceAll(from, to) }

  def filterAndSort(items : Seq[JsObject], orderBy : String, filter : String, filterFieldName : String) = {
    val filteredItems = if (filter.isEmpty) {
      items
    } else {
      val f = (filter + ".*").r
      items.filter { item =>
        val v = (item \ filterFieldName)
        f.unapplySeq(v.asOpt[String].getOrElse(v.toString())).isDefined
      }
    }

    val orderByField = if (orderBy.startsWith("-")) orderBy.substring(1) else orderBy

    val sortedItems = filteredItems.sortBy { item =>
      val v = (item \ orderByField)
      v.asOpt[String].getOrElse(v.toString())
    }

    if (orderBy.startsWith("-"))
      sortedItems.reverse
    else
      sortedItems
  }

  /**
    * Find items in field exactly matching input value.
    *
    * @param items Input items.
    * @param value Value for matching.
    * @param filterFieldName Field to be queried for value.
    * @return Found items.
    */
  def findBy(items : Seq[JsObject], value : String, filterFieldName : String) =
    items.filter { item =>
      val v = (item \ filterFieldName)
      v.asOpt[String].getOrElse(v.toString()).equals(value)
    }

  /**
    * Retrieve all items of specified field.
    *
    * @param items Input items.
    * @param fieldName Field of interest.
    * @return Items in specified field.
    */
  def project(items : Seq[JsObject], fieldName : String): Seq[JsLookupResult] =
    items.map { item => (item \ fieldName) }

  def projectDouble(items : Seq[JsObject], fieldName : String) : Seq[Option[Double]] =
    project(items, fieldName).map(toDouble)

  def projectString(items : Seq[JsObject], fieldName : String) : Seq[Option[String]] =
    project(items, fieldName).map(toString)

  def projectDate(items : Seq[JsObject], fieldName : String) : Seq[Option[Date]] =
    project(items, fieldName).map(toDate)

  def toDouble(jsValue : JsReadable) : Option[Double] =
    jsValue.asOpt[Double].map(Some(_)).getOrElse {
      jsValue.asOpt[String] match {
        case Some(string) =>
          try {
            Some(string.toDouble)
          } catch {
            case e: NumberFormatException => None
          }
        case None => None
      }
    }

  def toJsonNumber(value: String) : Option[JsNumber] = {
    try {
      Some(JsNumber(value.toDouble))
    } catch {
      case e: NumberFormatException => None
    }
  }

  def toString(value: JsReadable): Option[String] =
    value match {
      case JsNull => None
      case JsString(s) => Some(s)
      case JsNumber(n) => Some(n.toString)
      case JsDefined(json) => toString(json)
      case _ => Some(value.toString)
    }

  def toDate(value: JsReadable): Option[Date] =
    value match {
      case JsNull => None
      case JsString(s) => {
        val dateFormat = dateFormats.find{ format =>
          try {
            format.parse(s)
            true
          } catch {
            case e: ParseException => false
          }
        }

        dateFormat.map(_.parse(s)) match {
          case Some(date) => Some(date)
          case _ =>
            try {
              Some(new Date(s.toLong))
            } catch {
              case e: NumberFormatException => None
            }
        }
      }
      case JsNumber(n) => Some(new Date(n.toLong))
      case JsDefined(json) => toDate(json)
      case _ => None
    }

  def toFormattedDate(value: JsReadable): Option[String] =
    toDate(value).map(standardDateFormat.format)

  /**
    * Get smallest value of specified field. The values are cast to double before comparison.
    *
    * @param items Json items.
    * @param fieldName Name of the field for finding minimum.
    * @return Minimal value.
    */
  def getMin(items : Traversable[JsObject], fieldName : String): Double =
    items.map { item => (item \ fieldName).toString.toDouble }.min

  /**
    * Get greatest value of specified field. The values are cast to double before comparison.
    *
    * @param items Json items.
    * @param fieldName Name of the field for finding maximum.
    * @return Maximal value.
    */
  def getMax(items : Traversable[JsObject], fieldName : String): Double =
    items.map { item => (item \ fieldName).toString.toDouble }.max

  /**
    * Count objects of specified field to which the filter applies.
    *
    * @param items Json input items.
    * @param filter Filter string.
    * @param filterFieldName Name of he fields to be filtered.
    * @return Number of items to which the filter applies.
    */
  def count(items : Seq[JsObject], filter : String, filterFieldName : String): Int = {
    val filteredItems = if (filter.isEmpty) {
      items
    } else {
      val f = (filter + ".*").r
      items.filter { item =>
        val v = (item \ filterFieldName)
        f.unapplySeq(v.asOpt[String].getOrElse(v.toString())).isDefined
      }
    }
    filteredItems.length
  }
}