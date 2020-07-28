package org.ada.server.calc.impl

import akka.stream.scaladsl.Flow
import org.ada.server.calc.Calculator
import org.incal.core.akka.AkkaStreamUtil.seqFlow

trait GroupXOrderedSeqCalcTypePack[G, T] extends GroupXSeqCalcTypePack[G, T]

private[calc] final class GroupXOrderedSeqCalc[G, T: Ordering] extends Calculator[GroupXOrderedSeqCalcTypePack[G, T]] {

  private val basicCalc = GroupXSeqCalc.apply[G, T]

  override def fun(options: Unit) =
    (basicCalc.fun(())(_)) andThen
      (_.map { case (group, xSeqs) =>
        (group, xSeqs.toSeq.sortBy(_._1))
      })

  override def flow(options: Unit) =
    basicCalc.flow() map { values =>
      values.map { case (group, xSeqs) =>
        (group, xSeqs.toSeq.sortBy(_._1))
      }
    }

  override def postFlow(options: Unit) = identity
}

object GroupXOrderedSeqCalc {
  def apply[G, T: Ordering]: Calculator[GroupXOrderedSeqCalcTypePack[G, T]] = new GroupXOrderedSeqCalc[G, T]
}