package services.stats.calc

import akka.stream.scaladsl.Sink
import services.stats.Calculator
import play.api.Logger

import scala.collection.mutable
import scala.concurrent.Future
import DistanceCalcIOTypes._

object DistanceCalcIOTypes {
  type OUT = Seq[Seq[Double]]
  type INTER = Array[mutable.ArraySeq[Double]]
}

protected abstract class DistanceCalc[IN, OPT2, OPT3] extends Calculator[Seq[IN], OUT, INTER, Unit, OPT2, OPT3] {

  private val logger = Logger

  protected def dist(el1: IN, el2: IN): Option[Double]

  protected def processSum(sum: Double): Double

  protected def featuresNumAndGroupSizes: OPT2 => (Int, Seq[Int])

  override def fun(o: Unit) = { values: Traversable[Seq[IN]] =>
    val elementsCount = if (values.nonEmpty) values.head.size else 0

    def calc(index1: Int, index2: Int) = {
      // calculating distances and summing up
      val sum = values.foldLeft(0.0) { case (sum, els) =>
        dist(els(index1), els(index2)).map( distance =>
          sum + distance
        ).getOrElse(
          sum
        )
      }

      processSum(sum)
    }

    (0 until elementsCount).par.map { i =>
      (0 until elementsCount).par.map { j =>
        if (i != j) calc(i, j) else 0
      }.toList
    }.toList
  }

  override def sink(options: OPT2): Sink[Seq[IN], Future[INTER]] = {
    val (n, parallelGroupSizes) = featuresNumAndGroupSizes(options)

    val starts = parallelGroupSizes.scanLeft(0){_+_}
    val startEnds = parallelGroupSizes.zip(starts).map{ case (size, start) => (start, Math.min(start + size, n) - 1)}

    Sink.fold[INTER, Seq[IN]](
      (for (i <- 0 to n - 1) yield mutable.ArraySeq(Seq.fill(i)(0d): _*)).toArray
    ) {
      case (accumGlobal, featureValues) =>
        def calcAux(from: Int, to: Int) =
          for (i <- from to to) {
            val rowPSums = accumGlobal(i)
            val value1 = featureValues(i)
            for (j <- 0 to i - 1) {
              val value2 = featureValues(j)
              dist(value1, value2).foreach { distance =>
                rowPSums.update(j, rowPSums(j) + distance)
              }
            }
          }

        startEnds match {
          case Nil => calcAux(0, n - 1)
          case _ => startEnds.par.foreach((calcAux(_, _)).tupled)
        }
        accumGlobal
    }
  }

  override def postSink(options: OPT3) = { triangleResults: INTER =>
    val n = triangleResults.length
    logger.info("Generating a full matrix from the triangle sum results.")

    for (i <- 0 to n - 1) yield
      for (j <- 0 to n - 1) yield {
        if (i > j)
          processSum(triangleResults(i)(j))
        else if (i < j)
          processSum(triangleResults(j)(i))
        else
          0d
      }
  }
}