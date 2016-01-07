package util

import play.api.libs.json._
import collection.mutable.{Map => MMap}
import _root_.util.JsonUtil._

abstract class ChartSpec(title: String)
case class PieChartSpec(title: String, data: Seq[(String, Int)]) extends ChartSpec(title)
case class ColumnChartSpec(title: String, data: Seq[(String, Int)]) extends ChartSpec(title)
case class ScatterChartSpec(title: String, data: Seq[Seq[Double]]) extends ChartSpec(title)

object ChartSpec {

  def pie(
    items : Traversable[JsObject],
    fieldName : String
  ) : PieChartSpec = {
    val countMap = MMap[String, Int]()
    items.map{item =>
      val rawWalue = (item \ fieldName).get
      val stringValue = if (rawWalue == JsNull)
        "null"
      else
        rawWalue.as[String]
      val count = countMap.getOrElse(stringValue, 0)
      countMap.update(stringValue, count + 1)
    }
    PieChartSpec(fieldName, countMap.toSeq.sortBy(_._2))
  }

  def column(
    items : Traversable[JsObject],
    fieldName : String,
    columnCount : Int,
    explMin : Option[Double] = None,
    explMax : Option[Double] = None
  ) : ColumnChartSpec = {
    val values = project(items.toList, fieldName).map(toDouble).flatten
    val min = if (explMin.isDefined) explMin.get else values.min
    val max = if (explMax.isDefined) explMax.get else values.max
    val stepSize = (max - min) / columnCount

    val data = for (step <- 0 until columnCount) yield {
      val left = min + step * stepSize
      val right = left + stepSize
      val count = if (step == columnCount - 1) {
        values.filter(value => value >= left && value <= right).size
      } else {
        values.filter(value => value >= left && value < right).size
      }

      (left.toString, count)
    }
    ColumnChartSpec(fieldName, data.toSeq)
  }

  def scatter(
    items : Traversable[(JsObject, JsObject)],
    fieldName : String
  ) : ScatterChartSpec =
    ScatterChartSpec(fieldName,
      items.map { case (item1, item2) =>
        Seq(
          toDouble(item1 \ fieldName).get,
          toDouble(item2 \ fieldName).get
        )
      }.toSeq
    )

  private def toDouble(jsValue : JsLookupResult) : Option[Double] =
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
}