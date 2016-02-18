package runnables

import models.{FieldType, Field}
import persistence.{RepoSynchronizer, DictionaryFieldRepo}
import play.api.libs.json.{JsObject, Json, JsNull, JsString}
import util.TypeInferenceProvider
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

abstract class InferDictionary(dictionaryRepo: DictionaryFieldRepo) extends Runnable {

  protected val dataRepo = dictionaryRepo.dataRepo
  protected val timeout = 120000 millis

  protected def typeInferenceProvider : TypeInferenceProvider
  protected def uniqueCriteria : JsObject

  def run = {
    val dictionarySyncRepo = RepoSynchronizer(dictionaryRepo, timeout)
    // init dictionary if needed
    Await.result(dictionaryRepo.initIfNeeded, timeout)
    dictionarySyncRepo.deleteAll

    val futures = getFieldNames.filter(_ != "_id").par.map { fieldName =>
      println(fieldName)
      val fieldType = Await.result(inferType(fieldName), timeout)
      dictionaryRepo.save(Field(fieldName, fieldType, false))
    }

    // to be safe, wait for each save call to finish
    futures.toList.foreach(future => Await.result(future, timeout))
  }

  protected def inferType(fieldName : String) : Future[FieldType.Value] = {
    // get all the values for a given field
    dataRepo.find(None, None, Some(Json.obj(fieldName -> 1))).map { items =>
      val values = items.map { item =>
        val jsValue = (item \ fieldName).get
        jsValue match {
          case JsNull => None
          case x: JsString => Some(jsValue.as[String])
          case _ => Some(jsValue.toString)
        }
      }.flatten.toSet

      typeInferenceProvider.getType(values)
    }
  }

  protected def getFieldNames = {
    val syncDataRepo = RepoSynchronizer(dataRepo, timeout)
    val uniqueRecords = syncDataRepo.find(Some(uniqueCriteria))
    if (uniqueRecords.isEmpty)
      throw new IllegalStateException(s"No records found for $uniqueCriteria. The associated data set might be empty.")
    uniqueRecords.head.keys
  }
}