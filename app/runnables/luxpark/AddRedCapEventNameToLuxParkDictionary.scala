package runnables.luxpark

import javax.inject.Inject

import models.{FieldType, Field}
import runnables.DataSetId.lux_park_clinical
import persistence.dataset.DataSetAccessorFactory
import runnables.GuiceBuilderRunnable
import scala.concurrent.Await.result
import scala.concurrent.duration._

class AddRedCapEventNameToLuxParkDictionary @Inject() (
    dsaf: DataSetAccessorFactory
  ) extends Runnable {

  private val timeout = 120000 millis
  private val visitField = "redcap_event_name"

  override def run = {
    val dsa = dsaf(lux_park_clinical).get
    val fieldRepo = dsa.fieldRepo

    val saveFuture = fieldRepo.save(Field(visitField, FieldType.Enum))

    // to be safe, wait
    result(saveFuture, timeout)
  }
}

object AddRedCapEventNameToLuxParkDictionary extends GuiceBuilderRunnable[AddRedCapEventNameToLuxParkDictionary] with App { run }