package org.ada.server.calc.impl

import akka.stream.scaladsl.Flow
import org.ada.server.calc.{Calculator, CalculatorTypePack, NoOptionsCalculatorTypePack}
import org.incal.core.akka.AkkaStreamUtil._

trait XSeqCalcTypePack[T] extends NoOptionsCalculatorTypePack {
  type IN = Seq[Option[T]]
  type OUT = Seq[Option[T]]
  type INTER = OUT
}

private class XSeqCalc[T] extends Calculator[XSeqCalcTypePack[T]] {

  override def fun(options: Unit) = ???

  override def flow(options: Unit) = ???

  override def postFlow(options: Unit) = identity
}

object XSeqCalc {
  def apply[T]: Calculator[XSeqCalcTypePack[T]] = new XSeqCalc[T]
}