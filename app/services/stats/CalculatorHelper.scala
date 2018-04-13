package services.stats

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import models.Field
import play.api.libs.json.JsObject
import services.stats.calc.{ArrayCalc, GroupUniqueDistributionCountsCalc}
import services.stats.jsonin.JsonInputConverterFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe._

object CalculatorHelper {

  type NoOptionsCalculator[IN, OUT, INTER] = Calculator[IN, OUT, INTER, Unit, Unit, Unit]
  type FullDataCalculator[IN, OUT, OPT] = Calculator[IN, OUT, Traversable[IN], OPT, Unit, OPT]

  private val jicf = JsonInputConverterFactory

  implicit class RunExt[IN, OUT, INTER, OPT1, OPT2, OPT3](
    val calculator: Calculator[IN, OUT, INTER, OPT1, OPT2, OPT3]
  ) extends AnyVal {

    def runFlow(
      options2: OPT2,
      options3: OPT3)(
      source: Source[IN, _])(
      implicit materializer: Materializer
    ): Future[OUT] =
      source.via(calculator.flow(options2)).runWith(Sink.head).map(calculator.postFlow(options3))
  }

  implicit class JsonExt[
    IN: TypeTag, OUT, INTER, OPT1, OPT2, OPT3
  ](
    val calculator: Calculator[IN, OUT, INTER, OPT1, OPT2, OPT3]
  ) {
    def jsonFun(
      fields: Seq[Field],
      options: OPT1
    ): Traversable[JsObject] => OUT = {
      (jsons: Traversable[JsObject]) =>
        val jsonConverter = jsonConvert(fields)
        val inputs = jsons.map(jsonConverter)
        calculator.fun(options)(inputs)
    }

    def jsonFlow(
      fields: Seq[Field],
      options: OPT2
    ): Flow[JsObject, INTER, NotUsed] = {
      val jsonConverter = jsonConvert(fields)
      Flow[JsObject].map(jsonConverter).via(calculator.flow(options))
    }

    /**
      * Transforms jsons to the expected input and executes.
      * If the provided <code>scalarOrArrayField</code> is array, jsons are transformed to a sequence of arrays, otherwise plain scalars
      *
      * @param scalarOrArrayField
      * @param fields
      * @param options
      * @return
      */
    def jsonFunA(
      scalarOrArrayField: Field,
      fields: Seq[Field],
      options: OPT1
    ): Traversable[JsObject] => OUT =
        if (scalarOrArrayField.isArray)
          ArrayCalc(calculator).jsonFun(fields, options)
        else
          calculator.jsonFun(fields, options)

    /**
      * Creates a flow (stream) for an execution with inputs transformed from provided jsons.
      * If the provided <code>scalarOrArrayField</code> is array, each json is transformed to a array, otherwise a plain scalar
      *
      * @param scalarOrArrayField
      * @param fields
      * @param options
      * @return
      */
    def jsonFlowA(
      scalarOrArrayField: Field,
      fields: Seq[Field],
      options: OPT2
    ): Flow[JsObject, INTER, NotUsed] =
      if (scalarOrArrayField.isArray)
        ArrayCalc(calculator).jsonFlow(fields, options)
      else
        calculator.jsonFlow(fields, options)

    def runJsonFlow(
      fields: Seq[Field],
      options2: OPT2,
      options3: OPT3)(
      source: Source[JsObject, _])(
      implicit materializer: Materializer
    ): Future[OUT] =
      source.via(jsonFlow(fields, options2)).runWith(Sink.head).map(calculator.postFlow(options3))

    def runJsonFlowA(
      scalarOrArrayField: Field,
      fields: Seq[Field],
      options2: OPT2,
      options3: OPT3)(
      source: Source[JsObject, _])(
      implicit materializer: Materializer
    ): Future[OUT] =
      if (scalarOrArrayField.isArray)
        ArrayCalc(calculator).runJsonFlow(fields, options2, options3)(source)
      else
        calculator.runJsonFlow(fields, options2, options3)(source)

    private def jsonConvert(fields: Seq[Field]) = {
      val unwrappedCalculator =
        calculator match {
          case arrayCalc: ArrayCalc[_, _, _, _, _, _] => arrayCalc.innerCalculator
          case _ => calculator
        }
      jicf[IN](unwrappedCalculator).apply(fields)
    }
  }

  implicit class NoOptionsExt[IN, OUT, INTER](
    val calculator: NoOptionsCalculator[IN, OUT, INTER]
  ) {
    def fun_ = calculator.fun(())

    def flow_ = calculator.flow(())

    def postFlow_ = calculator.postFlow(())

    def runFlow_(
      source: Source[IN, _])(
      implicit materializer: Materializer
    ): Future[OUT] = calculator.runFlow((), ())(source)
  }

  implicit class NoOptionsJsonExt[
    IN: TypeTag, OUT, INTER
  ](
    val calculator: NoOptionsCalculator[IN, OUT, INTER]
  ) {
    def jsonFun_(fields: Field*) =
      calculator.jsonFun(fields, ())

    def jsonFunA_(scalarOrArrayField: Field, fields: Field*) =
      calculator.jsonFunA(scalarOrArrayField, fields, ())

    def jsonFlow_(fields: Field*) =
      calculator.jsonFlow(fields, ())

    def jsonFlowA_(scalarOrArrayField: Field, fields: Field*) =
      calculator.jsonFlowA(scalarOrArrayField, fields, ())

    def runJsonFlow_(
      fields: Field*)(
      source: Source[JsObject, _])(
      implicit materializer: Materializer
    ) =
      calculator.runJsonFlow(fields, (), ())(source)

    def runJsonFlowA_(
      scalarOrArrayField: Field,
      fields: Field*)(
      source: Source[JsObject, _])(
      implicit materializer: Materializer
    ) =
      calculator.runJsonFlowA(scalarOrArrayField, fields, (), ())(source)
  }
}