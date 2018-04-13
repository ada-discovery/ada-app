package services.stats.calc

import akka.stream.scaladsl.Flow
import services.stats.CalculatorHelper.NoOptionsCalculator
import services.stats.calc.TupleCalcIOTypes._
import util.AkkaStreamUtil._

object TupleCalcIOTypes {
  type IN[A, B] = (Option[A], Option[B])
  type OUT[A, B] = Traversable[(A, B)]
}

private class TupleCalc[A, B] extends NoOptionsCalculator[IN[A, B], OUT[A, B], OUT[A, B]] {

  override def fun(opt: Unit)  = _.flatMap(toOption)

  override def flow(options: Unit) = {
    val flatFlow = Flow.fromFunction(toOption).collect { case Some(x) => x}
    flatFlow.via(seqFlow)
  }

  override def postFlow(options: Unit) = identity

  private def toOption(ab: (Option[A], Option[B])) =
    ab._1.flatMap(a =>
      ab._2.map(b => (a, b))
    )
}

object TupleCalc {
  def apply[A, B]: NoOptionsCalculator[IN[A, B], OUT[A, B], OUT[A, B]] = new TupleCalc[A, B]
}