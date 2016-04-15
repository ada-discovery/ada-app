package runnables

import javax.inject.Inject

import models.{FieldType, Field}
import persistence.RepoSynchronizer
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json.{Json, JsNull, JsString}
import util.TypeInferenceProvider
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

class InferDictionary(
    dataSetId: String,
    typeInferenceProvider: TypeInferenceProvider
  ) extends Runnable {

  @Inject protected var dsaf: DataSetAccessorFactory = _
  protected lazy val dsa = dsaf(dataSetId).get
  protected lazy val dataRepo = dsa.dataSetRepo
  protected lazy val fieldRepo = dsa.fieldRepo
  protected lazy val categoryRepo = dsa.categoryRepo
  protected val timeout = 120000 millis

  def run = {
    val fieldSyncRepo = RepoSynchronizer(fieldRepo, timeout)
    // init dictionary if needed
    Await.result(fieldRepo.initIfNeeded, timeout)
    fieldSyncRepo.deleteAll

    val futures = getFieldNames.filter(_ != "_id").par.map { fieldName =>
      println(fieldName)
      val fieldType = Await.result(inferType(fieldName), timeout)

      fieldRepo.save(Field(fieldName, fieldType, false))
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
    val records = syncDataRepo.find(None, None, None, Some(1))
    if (records.isEmpty)
      throw new IllegalStateException(s"No records found. Unable to infer a dictionary. The associated data set might be empty.")
    records.head.keys
  }
}