package services.stats.calc

import akka.stream.scaladsl.{Flow, Keep, Sink}
import services.stats.{Calculator, NoOptionsCalculatorTypePack}
import util.AkkaStreamUtil._
import util.GroupMapList3

trait GroupTupleCalcTypePack[G, A, B] extends NoOptionsCalculatorTypePack {
  type IN = (Option[G], Option[A], Option[B])
  type OUT = Traversable[(Option[G], Traversable[(A, B)])]
  type INTER = Seq[(Option[G], Seq[(A, B)])]
}

private[stats] class GroupTupleCalc[G, A, B] extends Calculator[GroupTupleCalcTypePack[G, A, B]] {

  private val maxGroups = Int.MaxValue

  override def fun(opt: Unit)  =
    _.toGroupMap.map {
      case (groupValue, values) => (groupValue, values.flatMap(toOption))
    }

  override def flow(options: Unit) = {
    val flatFlow = Flow[IN].collect{ case (g, Some(x), Some(y)) => (g, (x, y)) }
    val groupedFlow = flatFlow.via(groupFlow[Option[G], (A, B)](maxGroups))

    groupedFlow.via(seqFlow)
  }

  override def postFlow(options: Unit) = identity

  private def toOption(ab: (Option[A], Option[B])) =
    ab._1.flatMap(a =>
      ab._2.map(b => (a, b))
    )
}

object GroupTupleCalc {
  def apply[G, A, B]: Calculator[GroupTupleCalcTypePack[G, A, B]] = new GroupTupleCalc[G, A, B]
}