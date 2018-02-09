package services.stats.calc

import akka.stream.scaladsl.{Flow, Keep, Sink}
import services.stats.NoOptionsCalculator
import services.stats.calc.GroupScatterCalcIOTypes._
import util.{GroupMapList, GroupMapList3}

object GroupScatterCalcIOTypes {
  type IN[G, A, B] = (Option[G], Option[A], Option[B])
  type OUT[G, A, B] = Traversable[(Option[G], Traversable[(A, B)])]
  type INTER[G, A, B] = Traversable[(Option[G], (A, B))]
}

private class GroupScatterCalc[G, A, B] extends NoOptionsCalculator[IN[G, A, B], OUT[G, A, B], INTER[G, A, B]] {

  override def fun(opt: Unit)  =
    _.toGroupMap.map {
      case (groupValue, values) => (groupValue, values.flatMap(toOption))
    }

  override def sink(options: Unit) = {
    val flatFlow: Flow[IN[G, A, B], (Option[G], (A, B)), _] = Flow.fromFunction(toOption2).collect{ case (g, Some(x)) => (g, x) }

    flatFlow.toMat(Sink.seq[(Option[G], (A, B))])(Keep.right)

    // TODO: could be optimzed by using groupBy directly with flows
//    val lalaFlow = flatFlow.groupBy(100, _._1).mergeSubstreams
//    lalaFlow.toMat(Sink.seq)(Keep.right)
  }

  override def postSink(options: Unit) = _.toGroupMap

  private def toOption(ab: (Option[A], Option[B])) =
    ab._1.flatMap(a =>
      ab._2.map(b => (a, b))
    )

  private def toOption2(gab: IN[G, A, B]) =
    (gab._1, toOption(gab._2, gab._3))
}

object GroupScatterCalc {
  def apply[G, A, B]: NoOptionsCalculator[IN[G, A, B], OUT[G, A, B], INTER[G, A, B]] = new GroupScatterCalc[G, A, B]
}