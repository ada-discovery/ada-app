package runnables.mpower

import com.banda.network.domain.ActivationFunctionType
import play.api.libs.json.{JsNull, JsNumber}
import runnables.DsaInputFutureRunnable

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class AddTanhReservoirFunction extends DsaInputFutureRunnable[AddTanhReservoirFunctionSpec] {

  private val funFieldName = "setting-reservoirFunctionType"
  private val tanhJsValue = JsNumber(ActivationFunctionType.Tanh.ordinal())

  override def runAsFuture(spec: AddTanhReservoirFunctionSpec) = {
    val repo = dataSetRepo(spec.dataSetId)

    for {
      // get all the entries
      all <- repo.find()

      // set the default function (TanH) and update
      _ <-  {
        val emptyFunctionItems =
          all.filter { json =>
            val jsValue = (json \ funFieldName).toOption
            jsValue.isEmpty || jsValue.get == JsNull
          }

        repo.update(
          emptyFunctionItems.map(_.+(funFieldName, tanhJsValue))
        )
      }
    } yield
      ()
  }

  override def inputType = typeOf[AddTanhReservoirFunctionSpec]
}

case class AddTanhReservoirFunctionSpec(dataSetId: String)