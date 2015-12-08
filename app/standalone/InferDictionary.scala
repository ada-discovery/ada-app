package standalone

import javax.inject.{Inject, Named}

import models.{Field, MetaTypeStats}
import persistence.{RepoSynchronizer, AsyncReadonlyRepo, DictionaryRepo}
import play.api.libs.json.{Json, JsNull, JsValue}

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.Await
import scala.concurrent.duration._

abstract class InferDictionary extends Runnable {

  protected def dictionaryRepo: DictionaryRepo

  private val timeout = 120000 millis

  def run = {
    // init dictionary if needed
    dictionaryRepo.initIfNeeded

    val syncDataRepo = RepoSynchronizer(dictionaryRepo.dataRepo, timeout)

    // get the keys (attributes)
    val uniqueCriteria = Some(Json.obj("Line_Nr" -> "1"))
    val fieldNames = syncDataRepo.find(uniqueCriteria).head.keys

    fieldNames.filter(_ != "_id").par.foreach { field =>
      println(field)
      // get all the values for a given field
      val values = syncDataRepo.find(None, None, Some(Json.obj(field -> 1))).map(item => (item \ field))
      println(values.toSet.size)
      Field(field)
    }
  }
//    val statsFuture = repo.find(None, Some(Json.obj("attributeName" -> 1)))
//    val globalCounts = new TypeCount
//
//    Await.result(statsFuture, timeout).foreach{ item =>
//      val valueFreqsWoNa = item.valueRatioMap.filterNot(_._1.toLowerCase.equals("na"))
//      val valuesWoNa = valueFreqsWoNa.keySet
//      val freqsWoNa = valueFreqsWoNa.values.toSeq
//
//      if (typeInferenceProvider.isNullOrNA(valuesWoNa))
//        globalCounts.nullOrNa += 1
//      else if (typeInferenceProvider.isBoolean(valuesWoNa))
//        globalCounts.boolean += 1
//      else if (typeInferenceProvider.isDate(valuesWoNa))
//        globalCounts.date += 1
//      else if (typeInferenceProvider.isNumberEnum(valuesWoNa, freqsWoNa))
//        globalCounts.numberEnum += 1
//      else if (typeInferenceProvider.isNumber(valuesWoNa))
//        globalCounts.freeNumber += 1
//      else if (typeInferenceProvider.isTextEnum(valuesWoNa, freqsWoNa))
//        globalCounts.textEnum += 1
//      else
//        globalCounts.freeText += 1
//    }
//
//    globalCounts
}

class InferDeNoPaBaselineDictionary extends InferDictionary {
  @Inject @Named("DeNoPaBaselineDictionaryRepo") var dictionaryRepo: DictionaryRepo = _
}

object InferDeNoPaBaselineDictionary extends GuiceBuilderRunnable[InferDeNoPaBaselineDictionary] with App {
  run
}