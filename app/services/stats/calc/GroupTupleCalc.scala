package services.stats.calc

import akka.stream.scaladsl.{Flow, Keep, Sink}
import services.stats.CalculatorHelper.NoOptionsCalculator
import services.stats.calc.GroupTupleCalcIOTypes._
import util.AkkaStreamUtil._
import util.GroupMapList3

object GroupTupleCalcIOTypes {
  type IN[G, A, B] = (Option[G], Option[A], Option[B])
  type OUT[G, A, B] = Traversable[(Option[G], Traversable[(A, B)])]
  type INTER[G, A, B] = Seq[(Option[G], Seq[(A, B)])]
}

private[stats] class GroupTupleCalc[G, A, B] extends NoOptionsCalculator[IN[G, A, B], OUT[G, A, B], INTER[G, A, B]] {

  private val maxGroups = Int.MaxValue

  override def fun(opt: Unit)  =
    _.toGroupMap.map {
      case (groupValue, values) => (groupValue, values.flatMap(toOption))
    }

  override def flow(options: Unit) = {
    val flatFlow = Flow.fromFunction(toOption2).collect{ case (g, Some(x)) => (g, x) }
    val groupedFlow = flatFlow.via(groupFlow[Option[G], (A, B)](maxGroups))

    groupedFlow.via(seqFlow)
  }

  override def postFlow(options: Unit) = identity

  private def toOption(ab: (Option[A], Option[B])) =
    ab._1.flatMap(a =>
      ab._2.map(b => (a, b))
    )

  private def toOption2(gab: IN[G, A, B]) =
    (gab._1, toOption(gab._2, gab._3))
}

object GroupTupleCalc {
  def apply[G, A, B]: NoOptionsCalculator[IN[G, A, B], OUT[G, A, B], INTER[G, A, B]] = new GroupTupleCalc[G, A, B]
}