package util

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip, ZipN}
import scala.collection.mutable.{Buffer, ListBuffer}

import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object AkkaStreamUtil {

  def groupCountFlow[A](
    maxSubstreams: Int
  ): Flow[A, (A, Int), NotUsed] =
    Flow[A]
      .groupBy(maxSubstreams, identity)
      .map { a => a -> 1}
      .reduce((l, r) ⇒ (l._1, l._2 + r._2))
      .mergeSubstreams

  def uniqueFlow[A](
    maxSubstreams: Int
  ): Flow[A, A, NotUsed] =
    Flow[A]
      .groupBy(maxSubstreams, identity)
      .reduce((l, _) ⇒ l)
      .mergeSubstreams

  def groupCountFlowTuple[A, B](
    maxSubstreams: Int
  ): Flow[(A, B), (A, Int), NotUsed] =
    Flow[(A, B)]
      .groupBy(maxSubstreams, _._1)
      .map { case (a, _) => a -> 1}
      .reduce((l, r) ⇒ (l._1, l._2 + r._2))
      .mergeSubstreams

  def groupFlow[A, B](
    maxSubstreams: Int = Int.MaxValue
  ): Flow[(A, B), (A, Seq[B]), NotUsed] =
    Flow[(A,B)]
      .groupBy(maxSubstreams, _._1)
      .map { case (a, b) => a -> Buffer(b)}
      .reduce((l, r) ⇒ (l._1, {l._2.appendAll(r._2); l._2}))
      .mergeSubstreams

  def zipSources[A, B](
    source1: Source[A, _],
    source2: Source[B, _]
  ): Source[(A, B), NotUsed] =
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      // prepare graph elements
      val zip = b.add(Zip[A, B]())

      // connect the graph
      source1 ~> zip.in0
      source2 ~> zip.in1

      // expose port
      SourceShape(zip.out)
    })

  def zipNFlows[T, U](
    flows: Seq[Flow[T, U, NotUsed]])(
  ): Flow[T, Seq[U], NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val bcast = b.add(Broadcast[T](flows.size))
      val zipper = b.add(ZipN[U](flows.size))
      val flowsB = flows.map(flow => b.add(flow))

      flowsB.zipWithIndex.foreach { case (flow, i) => bcast.out(i) ~> flow.in }
      flowsB.zipWithIndex.foreach { case (flow, i) => flow.out ~> zipper.in(i)}

      FlowShape(bcast.in, zipper.out)
    })

  def seqFlow[T]: Flow[T, Seq[T], NotUsed] =
    Flow[T].fold(Vector.newBuilder[T]) { _ += _ }.map(_.result)
}

object AkkaTest extends App {
  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  val source = Source(List(0, 1, 2))

  val sumFlow = Flow[Int].fold(0)(_+_)
  val minFlow = Flow[Int].fold(Integer.MAX_VALUE)(Math.min)
  val maxFlow = Flow[Int].fold(Integer.MIN_VALUE)(Math.max)
  val combinedFlow = AkkaStreamUtil.zipNFlows(Seq(sumFlow, minFlow, maxFlow))

  val resultsFuture = source.via(combinedFlow).runWith(Sink.head)
  val results = Await.result(resultsFuture, 1 minute)

  println(results.mkString("\n"))
}