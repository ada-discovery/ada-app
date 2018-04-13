package services.stats

import akka.NotUsed
import akka.stream.scaladsl.Flow
import services.stats.CalculatorHelper.FullDataCalculator
import util.AkkaStreamUtil.seqFlow

trait Calculator[IN, OUT, INTER, OPT1, OPT2, OPT3] {

  def fun(options: OPT1): Traversable[IN] => OUT

  def flow(options: OPT2): Flow[IN, INTER, NotUsed]

  def postFlow(options: OPT3): INTER => OUT
}

trait FullDataCalculatorAdapter[IN, OUT, OPT] extends FullDataCalculator[IN, OUT, OPT] {

  // need to get all the data so collect
  override def flow(options: Unit) = seqFlow[IN]

  // same as calc
  override def postFlow(options: OPT) = fun(options)
}