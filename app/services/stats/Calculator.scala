package services.stats

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

trait Calculator[IN, OUT, INTER, OPT1, OPT2, OPT3] {

  def fun(options: OPT1): Traversable[IN] => OUT

  def sink(options: OPT2): Sink[IN, Future[INTER]]

  def postSink(options: OPT3): INTER => OUT

  // run the stream with a selected sink and process with a post-sink
  def runSink(
    options2: OPT2,
    options3: OPT3)(
    source: Source[IN, _])(
    implicit materializer: Materializer
  ): Future[OUT] =
    source.runWith(sink(options2)).map(
      postSink(options3)
    )
}

trait NoOptionsCalculator[IN, OUT, INTER] extends  Calculator[IN, OUT, INTER, Unit, Unit, Unit]

trait FullDataCalculator[IN, OUT, OPT] extends Calculator[IN, OUT, Traversable[IN], OPT, Unit, OPT] {

  // need to get all the data so collect
  override def sink(options: Unit) = Sink.seq

  // same as calc
  override def postSink(options: OPT) = fun(options)
}