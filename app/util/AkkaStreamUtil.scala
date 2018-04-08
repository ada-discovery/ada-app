package util

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.javadsl.ZipWithN
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip, ZipN}
import play.api.libs.json.JsObject
import shapeless.ops.hlist.ZipWith

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

  def groupCountFlowTuple[A, B](
    maxSubstreams: Int
  ): Flow[(A, B), (A, Int), NotUsed] =
    Flow[(A,B)]
      .groupBy(maxSubstreams, _._1)
      .map { case (a, _) => a -> 1}
      .reduce((l, r) ⇒ (l._1, l._2 + r._2))
      .mergeSubstreams

  def groupFlow[A, B](
    maxSubstreams: Int
  ): Flow[(A, B), (A, Seq[B]), NotUsed] =
    Flow[(A,B)]
      .groupBy(maxSubstreams, _._1)
      .map { case (a, b) => a -> Seq(b)}
      .reduce((l, r) ⇒ (l._1, l._2 ++ r._2))
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

  /**
    * Combine several sinks with fan-out strategy like `Broadcast` or `Balance` and returns `Sink`.
    */
  def combine[T, U, R](
    first: Sink[U, Future[R]],
    second: Sink[U, Future[R]],
    rest: Sink[U, Future[R]]*)(
    strategy: Int ⇒ Graph[UniformFanOutShape[T, U], NotUsed]
  ): Sink[T, NotUsed] = {
//    val finalSink = Sink[T, Future[R]]

    Sink.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._
      val d = b.add(strategy(rest.size + 2))
      d.out(0) ~> first
      d.out(1) ~> second

      @tailrec def combineRest(idx: Int, i: Iterator[Sink[U, Future[R]]]): SinkShape[T] =
        if (i.hasNext) {
          d.out(idx) ~> i.next()
          combineRest(idx + 1, i)
        } else new SinkShape(d.in)

      combineRest(2, rest.iterator)
    })
  }

  private def ssad(implicit materializer: ActorMaterializer) = {
    val topHeadSink = Sink.head[Int]
    val bottomHeadSink = Sink.head[Int]
    val sharedDoubler = Flow[Int].map(_ * 2)

    val lala = RunnableGraph.fromGraph(GraphDSL.create(topHeadSink, bottomHeadSink)((_, _)) { implicit builder =>
      (topHS, bottomHS) =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[Int](2))
        Source.single(1) ~> broadcast.in

        broadcast.out(0) ~> sharedDoubler ~> topHS.in
        broadcast.out(1) ~> sharedDoubler ~> bottomHS.in
        ClosedShape
    })
    lala.run()
  }

  private def connectSinkToSource[T, U](
    source: Source[T, _],
    sink: Sink[T, Future[U]])(
    implicit materializer: ActorMaterializer
  ) = {
    val topHeadSink = Sink.head[Int]
    val bottomHeadSink = Sink.head[Int]

    val lala = RunnableGraph.fromGraph(GraphDSL.create(sink, sink)((_, _)) { implicit builder =>
      (sink1, sink2) =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[T](2))
        source ~> broadcast.in

        broadcast.out(0) ~> sink1.in
        broadcast.out(1) ~> sink2.in

        ClosedShape
    })
    lala.run()
  }

  private def connectSourceToSinks[T, U](
    source: Source[T, _],
    sinks: Traversable[Sink[T, Future[U]]])(
  ) = {


//    val sink1 = Sink.fold[Int, Int](0)(_ + _)
//    val sink2 = Sink.fold[Int, Int](0)(_ + _)
//
//    val sink = Sink.combine(sink1, sink2)(Broadcast[Int](_))
//
//    Source(List(0, 1, 2)).runWith(sink)

    val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val bcast = builder.add(Broadcast[T](sinks.size))

      source ~> bcast

      sinks.toSeq.zipWithIndex.foreach { case (sink, i) =>
        bcast.out(i) ~> sink
      }

      ClosedShape
    })
  }

  private def ddasdsa() = {
    val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val in = Source(1 to 10)
      val out = Sink.ignore

      val bcast = builder.add(Broadcast[Int](2))
      val merge = builder.add(Merge[Int](2))

      val f1, f2, f3, f4 = Flow[Int].map(_ + 10)

      in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
      bcast ~> f4 ~> merge
      ClosedShape
    })
  }
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