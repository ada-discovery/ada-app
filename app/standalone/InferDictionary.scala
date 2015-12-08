package standalone

import models.MetaTypeStats
import play.api.libs.json.{JsNull, JsValue}
import scala.concurrent.duration._
import scala.collection.mutable.{Map => MMap}
import services.DeNoPaSetting._

class InferDictionary {

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

    if (typeInferenceProvider.isBoolean(text))
      cumulativeStats.booleanCount += 1
    else if (typeInferenceProvider.isInt(text))
      cumulativeStats.intCount += 1
    else if (typeInferenceProvider.isLong(text))
    cumulativeStats.longCount += 1
    else if (typeInferenceProvider.isFloat(text))
      cumulativeStats.floatCount += 1
    else if (typeInferenceProvider.isDouble(text))
      cumulativeStats.doubleCount += 1

    // value counts
    val count = cumulativeStats.valueCountMap.getOrElse(text, 0)
    cumulativeStats.valueCountMap.update(text, count + 1)
  }
}