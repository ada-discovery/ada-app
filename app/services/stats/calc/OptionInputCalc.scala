package services.stats.calc

import akka.stream.scaladsl.{Flow, Keep}
import services.stats.Calculator

private[stats] trait OptionInputCalc[IN, OUT, INTER, OPT1, OPT2, OPT3] extends Calculator[Option[IN], OUT, INTER, OPT1, OPT2, OPT3] {

  protected val allDefinedCalc: Calculator[IN, OUT, INTER, OPT1, OPT2, OPT3]

  override def fun(options: OPT1) =
    (values) => allDefinedCalc.fun(options)(values.flatten)

  override def flow(options: OPT2) = {
    val allDefinedFlow = allDefinedCalc.flow(options)
    val flatFlow = Flow[Option[IN]].collect { case Some(x) => x }
    flatFlow.via(allDefinedFlow)
  }

  override def postFlow(options: OPT3) =
    allDefinedCalc.postFlow(options)
}