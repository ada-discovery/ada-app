package runnables.mpower

import com.bnd.network.domain.ActivationFunctionType
import play.api.libs.json.{JsNull, JsNumber}
import runnables.DsaInputFutureRunnable

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class AddTanhReservoirFunction extends DsaInputFutureRunnable[AddTanhReservoirFunctionSpec] {

  private val funFieldName = "setting-reservoirFunctionType"
  private val tanhJsValue = JsNumber(ActivationFunctionType.Tanh.ordinal())

  override def runAsFuture(spec: AddTanhReservoirFunctionSpec) =
    for {
      repo <- createDataSetRepo(spec.dataSetId)

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

case class AddTanhReservoirFunctionSpec(dataSetId: String)