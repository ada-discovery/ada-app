package util

import play.api.libs.json.{JsString, JsNull, JsObject}

object JsonUtil {

  def escapeKey(key : String) =
    key.replaceAll("\\.", "\\u002e") // \u2024// replaceAll("\\", "\\\\").replaceAll("\\$", "\\u0024").

  def unescapeKey(key : String) =
    key.replaceAll("u002e", "\\.") // .replaceAll("\\u0024", "\\$").replaceAll("\\\\", "\\")

  def jsonObjectsToCsv(
    delimiter : String,
    newLine : String = "\n",
    replacements : Iterable[(String, String)]
  )(items : Traversable[JsObject]) = {
    val sb = new StringBuilder(10000)

    val replaceAllAux = replaceAll(replacements)_

    if (!items.isEmpty) {
      val header = items.head.fields.map{ case (field, value) => unescapeKey(replaceAllAux(field))}.mkString(delimiter)
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

  def findBy(items : Seq[JsObject], value : String, filterFieldName : String) =
    items.filter { item =>
      val v = (item \ filterFieldName)
      v.asOpt[String].getOrElse(v.toString()).equals(value)
    }

  def project(items : Seq[JsObject], fieldName : String) =
    items.map { item => (item \ fieldName) }

  def getMin(items : Traversable[JsObject], fieldName : String) =
    items.map { item => (item \ fieldName).toString.toDouble }.min

  def getMax(items : Traversable[JsObject], fieldName : String) =
    items.map { item => (item \ fieldName).toString.toDouble }.max
}