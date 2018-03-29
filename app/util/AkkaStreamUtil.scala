package util

import akka.NotUsed
import akka.stream.{ActorMaterializer, ClosedShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}
import play.api.libs.json.JsObject

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

  private def connectSinksToSource[T, U](
    source: Source[T, _],
    sinks: Traversable[Sink[U, _]])(
    implicit materializer: ActorMaterializer
  ) = {
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
