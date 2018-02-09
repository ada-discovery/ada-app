package services.stats.calc

import akka.stream.scaladsl.{Flow, Keep, Sink}
import services.stats.NoOptionsCalculator
import services.stats.calc.ScatterCalcIOTypes._

object ScatterCalcIOTypes {
  type IN[A, B] = (Option[A], Option[B])
  type OUT[A, B] = Traversable[(A, B)]
}

private class ScatterCalc[A, B] extends NoOptionsCalculator[IN[A, B], OUT[A, B], OUT[A, B]] {

  override def fun(opt: Unit)  = _.flatMap(toOption)

  override def sink(options: Unit) = {
    val flatFlow = Flow.fromFunction(toOption).collect { case Some(x) => x}
    flatFlow.toMat(Sink.seq[(A, B)])(Keep.right)
  }

  override def postSink(options: Unit) = identity

  private def toOption(ab: (Option[A], Option[B])) =
    ab._1.flatMap(a =>
      ab._2.map(b => (a, b))
    )
}

object ScatterCalc {
  def apply[A, B]: NoOptionsCalculator[IN[A, B], OUT[A, B], OUT[A, B]] = new ScatterCalc[A, B]
}