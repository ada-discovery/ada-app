package util

import java.text.{ParseException, SimpleDateFormat}

import com.fasterxml.jackson.core.JsonParseException
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
            try {
              val filterJson = Json.parse(jsonString)
              Right(filterJson.as[E])
            } catch {
              case e: JsonParseException => Left("Unable to bind JSON from String to " + key)
            }
          }
          case _ => Left("Unable to bind JSON from String to " + key)
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
  def project(items : Traversable[JsObject], fieldName : String): Traversable[JsLookupResult] =
    items.map { item => (item \ fieldName) }

  def toString(value: JsReadable): Option[String] =
    value match {
      case JsNull => None
      case JsString(s) => Some(s)
      case JsNumber(n) => Some(n.toString)
      case JsDefined(json) => toString(json)
      case _: JsUndefined => None
      case _ => Some(value.toString)
    }

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