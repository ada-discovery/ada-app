package services.stats

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import util.AkkaStreamUtil.seqFlow

trait Calculator[IN, OUT, INTER, OPT1, OPT2, OPT3] {

  def fun(options: OPT1): Traversable[IN] => OUT

  def flow(options: OPT2): Flow[IN, INTER, NotUsed]

  def postFlow(options: OPT3): INTER => OUT

  // run the stream with a selected sink and process with a post-sink
  def runSink(
    options2: OPT2,
    options3: OPT3)(
    source: Source[IN, _])(
    implicit materializer: Materializer
  ): Future[OUT] =
    source.via(flow(options2)).runWith(Sink.head).map(postFlow(options3))
}

trait NoOptionsCalculator[IN, OUT, INTER] extends  Calculator[IN, OUT, INTER, Unit, Unit, Unit]

trait FullDataCalculator[IN, OUT, OPT] extends Calculator[IN, OUT, Traversable[IN], OPT, Unit, OPT] {

  // need to get all the data so collect
  override def flow(options: Unit) = seqFlow[IN]

  // same as calc
  override def postFlow(options: OPT) = fun(options)
}