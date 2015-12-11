package standalone

import javax.inject.{Inject, Named}

import models.Field
import persistence.{RepoSynchronizer, DictionaryRepo}
import play.api.libs.json.{JsObject, Json, JsNull}
import services.DeNoPaSetting
import util.TypeInferenceProvider

import scala.concurrent.duration._

abstract class InferDictionary extends Runnable {

  protected def dictionaryRepo: DictionaryRepo
  protected def typeInferenceProvider : TypeInferenceProvider
  protected def uniqueCriteria : JsObject

  private val timeout = 120000 millis

  def run = {
    val dictionarySyncRepo = RepoSynchronizer(dictionaryRepo, timeout)
    val syncDataRepo = RepoSynchronizer(dictionaryRepo.dataRepo, timeout)

    val startTime = new java.util.Date()

    // init dictionary if needed
    dictionaryRepo.initIfNeeded
    dictionarySyncRepo.deleteAll

    // get the field names
    val fieldNames = syncDataRepo.find(Some(uniqueCriteria)).head.keys

    fieldNames.filter(_ != "_id").par.foreach { fieldName =>
      println(fieldName)
      // get all the values for a given field
      val values = syncDataRepo.find(None, None, Some(Json.obj(fieldName -> 1))).map { item =>
        val jsValue = (item \ fieldName).get
        jsValue match {
          case JsNull => None
          case _ => Some(jsValue.as[String])
        }
      }.flatten.toSet

      val field = Field(fieldName, typeInferenceProvider.getType(values))
      dictionaryRepo.save(field)
    }

    val endTime = new java.util.Date()
    val elapsedTimeInSecs = (endTime.getTime - startTime.getTime) / 1000
    println(elapsedTimeInSecs)
  }
}

class InferDeNoPaBaselineDictionary extends InferDictionary {
  @Inject @Named("DeNoPaBaselineDictionaryRepo") var dictionaryRepo: DictionaryRepo = _
  val typeInferenceProvider = DeNoPaSetting.typeInferenceProvider
  val uniqueCriteria = Json.obj("Line_Nr" -> "1")
}

object InferDeNoPaBaselineDictionary extends GuiceBuilderRunnable[InferDeNoPaBaselineDictionary] with App {
  run
}