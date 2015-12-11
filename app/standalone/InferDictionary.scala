package standalone

import javax.inject.{Inject, Named}

import models.Field
import persistence.{RepoSynchronizer, DictionaryRepo}
import play.api.libs.json.{JsObject, Json, JsNull, JsString}
import services.DeNoPaSetting
import util.TypeInferenceProvider

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class InferDictionary(dictionaryRepo: DictionaryRepo) extends Runnable {

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

    val futures = fieldNames.filter(_ != "_id").par.map { fieldName =>
      println(fieldName)
      // get all the values for a given field
      val values = syncDataRepo.find(None, None, Some(Json.obj(fieldName -> 1))).map { item =>
        val jsValue = (item \ fieldName).get
        jsValue match {
          case JsNull => None
          case x : JsString => Some(jsValue.as[String])
          case _ => Some(jsValue.toString)
        }
      }.flatten.toSet

      val field = Field(fieldName, typeInferenceProvider.getType(values))
      dictionaryRepo.save(field)
    }

    // to be safe, wait for each save call to finish
    futures.toList.foreach(future => Await.result(future, timeout))

    val endTime = new java.util.Date()
    val elapsedTimeInSecs = (endTime.getTime - startTime.getTime) / 1000
    println(elapsedTimeInSecs)
  }
}

protected abstract class InferDeNoPaDictionary(dictionaryRepo: DictionaryRepo) extends InferDictionary(dictionaryRepo) {
  override protected val typeInferenceProvider = DeNoPaSetting.typeInferenceProvider
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> "1")
}

class InferDeNoPaBaselineDictionary @Inject()(
  @Named("DeNoPaBaselineDictionaryRepo") dictionaryRepo: DictionaryRepo) extends InferDeNoPaDictionary(dictionaryRepo)

class InferDeNoPaFirstVisitDictionary @Inject()(
  @Named("DeNoPaFirstVisitDictionaryRepo") dictionaryRepo: DictionaryRepo) extends InferDeNoPaDictionary(dictionaryRepo)

class InferDeNoPaCuratedBaselineDictionary @Inject()(
  @Named("DeNoPaCuratedBaselineDictionaryRepo") dictionaryRepo: DictionaryRepo) extends InferDeNoPaDictionary(dictionaryRepo) {
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> 1)
}

class InferDeNoPaCuratedFirstVisitDictionary @Inject()(
  @Named("DeNoPaCuratedFirstVisitDictionaryRepo") dictionaryRepo: DictionaryRepo) extends InferDeNoPaDictionary(dictionaryRepo) {
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> 1)
}

object InferDeNoPaBaselineDictionary extends GuiceBuilderRunnable[InferDeNoPaBaselineDictionary] with App { run }
object InferDeNoPaFirstVisitDictionary extends GuiceBuilderRunnable[InferDeNoPaFirstVisitDictionary] with App { run }
object InferDeNoPaCuratedBaselineDictionary extends GuiceBuilderRunnable[InferDeNoPaCuratedBaselineDictionary] with App { run }
object InferDeNoPaCuratedFirstVisitDictionary extends GuiceBuilderRunnable[InferDeNoPaCuratedFirstVisitDictionary] with App { run }