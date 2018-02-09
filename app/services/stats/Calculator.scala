package services.stats

import akka.stream.scaladsl.Sink
import scala.concurrent.Future

trait Calculator[IN, OUT, INTER, OPT1, OPT2, OPT3] {

  def fun(options: OPT1): Traversable[IN] => OUT

  def sink(options: OPT2): Sink[IN, Future[INTER]]

  def postSink(options: OPT3): INTER => OUT
}

trait NoOptionsCalculator[IN, OUT, INTER] extends  Calculator[IN, OUT, INTER, Unit, Unit, Unit]

trait FullDataCalculator[IN, OUT, OPT] extends Calculator[IN, OUT, Traversable[IN], OPT, Unit, OPT] {

  // need to get all the data so collect
  override def sink(options: Unit) = Sink.seq

  // same as calc
  override def postSink(options: OPT) = fun(options)
}