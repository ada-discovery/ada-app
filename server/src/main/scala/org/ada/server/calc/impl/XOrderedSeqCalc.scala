package org.ada.server.calc.impl

import org.ada.server.calc.Calculator

trait XOrderedSeqCalcTypePack[T] extends XSeqCalcTypePack[T]

private class XOrderedSeqCalc[T: Ordering] extends Calculator[XOrderedSeqCalcTypePack[T]] {

  private val basicCalc = XSeqCalc.apply[T]

  override def fun(options: Unit) =
    (basicCalc.fun(())(_)) andThen (_.toSeq.sortBy(_._1))

  override def flow(options: Unit) =
    basicCalc.flow() map (_.toSeq.sortBy(_._1))

  override def postFlow(options: Unit) = identity
}

object XOrderedSeqCalc {
  def apply[T: Ordering]: Calculator[XOrderedSeqCalcTypePack[T]] = new XOrderedSeqCalc[T]
}