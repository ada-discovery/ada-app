package standalone

import javax.inject.Inject

import models.MetaTypeStats
import play.api.libs.json.{JsNull, JsValue, JsObject, Json}
import scala.concurrent.duration._
import scala.collection.mutable.{Map => MMap}
import scala.concurrent.{Future, Await}

class InferTypeDeNoPa {

  val timeout = 120000 millis

  class MetaTypeCounts(
    var intCount: Int = 0,
    var longCount: Int = 0,
    var floatCount: Int = 0,
    var doubleCount: Int = 0,
    var booleanCount: Int = 0,
    var nullCount: Int = 0,
    var valueCountMap: MMap[String, Int] = MMap[String, Int]()
  ) {
    override def toString() : String = s"Int : $intCount, long: $longCount, float : $floatCount, double : $doubleCount, boolean : $booleanCount, null : $nullCount, value count map: $valueCountMap"
  }

  protected def createStats(attributeName : String, counts : MetaTypeCounts, fullCount : Int) = MetaTypeStats(
    None,
    attributeName,
    counts.intCount.toDouble / fullCount,
    counts.longCount.toDouble / fullCount,
    counts.floatCount.toDouble / fullCount,
    counts.doubleCount.toDouble / fullCount,
    counts.booleanCount.toDouble / fullCount,
    counts.nullCount.toDouble / fullCount,
    counts.valueCountMap.toMap.map{case (key, count) => (key, count.toDouble / fullCount)}
  )

  protected def inferType(jsValue : JsValue, cumulativeStats : MetaTypeCounts) {
    if (jsValue == JsNull) {
      cumulativeStats.nullCount += 1
      return
    }

    val text = jsValue.as[String]
    // integer type
    try {
      text.toInt
      cumulativeStats.intCount += 1
    } catch {
      case t: NumberFormatException => // nothing to do
    }

    // long type
    try {
      text.toLong
      cumulativeStats.longCount += 1
    } catch {
      case t: NumberFormatException => // nothing to do
    }

    // float type
    try {
      text.toFloat
      cumulativeStats.floatCount += 1
    } catch {
      case t: NumberFormatException => // nothing to do
    }

    // double type
    try {
      text.toDouble
      cumulativeStats.doubleCount += 1
    } catch {
      case t: NumberFormatException => // nothing to do
    }

    // boolean type
    try {
      text.toBoolean
      cumulativeStats.booleanCount += 1
    } catch {
      case t: IllegalArgumentException => // nothing to do
    }

    // value counts
    val count = cumulativeStats.valueCountMap.getOrElse(text, 0)
    cumulativeStats.valueCountMap.update(text, count + 1)
  }
}