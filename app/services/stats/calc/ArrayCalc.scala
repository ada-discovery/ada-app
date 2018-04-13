package services.stats.calc

import akka.stream.scaladsl.Flow
import services.stats.Calculator

private[stats] class ArrayCalc[IN, OUT, INTER, OPT1, OPT2, OPT3](
    val innerCalculator: Calculator[IN, OUT, INTER, OPT1, OPT2, OPT3]
  ) extends Calculator[Array[IN], OUT, INTER, OPT1, OPT2, OPT3] {

  override def fun(options: OPT1) =
    (values) => innerCalculator.fun(options)(values.flatten)

  override def flow(options: OPT2) =
    Flow[Array[IN]].mapConcat(_.toList).via(innerCalculator.flow(options))

  override def postFlow(options: OPT3) =
    innerCalculator.postFlow(options)
}

object ArrayCalc {

  def apply[IN, OUT, INTER, OPT1, OPT2, OPT3]
    (calculator: Calculator[IN, OUT, INTER, OPT1, OPT2, OPT3]
  ): Calculator[Array[IN], OUT, INTER, OPT1, OPT2, OPT3] = new ArrayCalc(calculator)
}