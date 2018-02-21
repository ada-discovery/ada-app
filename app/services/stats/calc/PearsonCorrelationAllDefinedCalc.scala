package services.stats.calc

import akka.stream.scaladsl.Sink
import services.stats.Calculator
import services.stats.calc.PearsonCorrelationAllDefinedCalcIOTypes._
import _root_.util.GrouppedVariousSize
import play.api.Logger

import scala.collection.mutable
import scala.concurrent.Future

object PearsonCorrelationAllDefinedCalcIOTypes {
  type IN = Seq[Double]
  type OUT = Seq[Seq[Option[Double]]]
  type INTER = PersonIterativeAccumGlobalArray
}

object PearsonCorrelationAllDefinedCalc extends Calculator[IN, OUT, INTER, Unit, (Int, Seq[Int]), Seq[Int]] {

  private val logger = Logger

  override def fun(o: Unit) = { values: Traversable[IN] =>
    ???
  }

  override def sink(featuresNumAndGroupSizes: (Int, Seq[Int])): Sink[IN, Future[INTER]] = {
    val n = featuresNumAndGroupSizes._1
    val parallelGroupSizes = featuresNumAndGroupSizes._2

    val starts = parallelGroupSizes.scanLeft(0){_+_}
    val startEnds = parallelGroupSizes.zip(starts).map{ case (size, start) => (start, Math.min(start + size, n) - 1)}

    Sink.fold[PersonIterativeAccumGlobalArray, IN](
      PersonIterativeAccumGlobalArray(
        sumSqSums = for (i <- 0 to n - 1) yield (0d, 0d),
        pSums = (for (i <- 0 to n - 1) yield mutable.ArraySeq(Seq.fill(i)(0d): _*)).toArray,
        count = 0
      )
    ) {
      case (accumGlobal, featureValues) =>
        logger.info("Executing an iteration of Pearson correlation")
        val newSumSqSums = (accumGlobal.sumSqSums, featureValues).zipped.map { case ((sum, sqSum), value) =>
          (sum + value, sqSum + value * value)
        }

        val pSums = accumGlobal.pSums

        def calcAux(from: Int, to: Int) =
          for (i <- from to to) {
            val rowPSums = pSums(i)
            val value1 = featureValues(i)
            for (j <- 0 to i - 1) {
              val value2 = featureValues(j)
              rowPSums.update(j, rowPSums(j) + value1 * value2)
            }
          }

        startEnds match {
          case Nil => calcAux(0, n - 1)
          case _ => startEnds.par.foreach((calcAux(_, _)).tupled)
        }
        PersonIterativeAccumGlobalArray(newSumSqSums, pSums, accumGlobal.count + 1)
    }
  }

  @Deprecated
  private def sinkOld(
    n: Int,
    parallelGroupSizes: Seq[Int]
  ) =
    Sink.fold[PersonIterativeAccumGlobal, Seq[Double]](
      PersonIterativeAccumGlobal(
        sumSqSums = for (i <- 0 to n - 1) yield (0d, 0d),
        pSums = for (i <- 0 to n - 1) yield Seq.fill(i)(0d),
        count = 0
      )
    ) {
      case (accumGlobal, featureValues) =>
        val newSumSqSums = (accumGlobal.sumSqSums, featureValues).zipped.map { case ((sum, sqSum), value) =>
          (sum + value, sqSum + value * value)
        }

        def calcAux(pSumValuePairs: Traversable[(Seq[Double], Double)]) =
          pSumValuePairs.map { case (pSums, value1) =>
            (pSums, featureValues).zipped.map { case (pSum, value2) =>
              pSum + value1 * value2
            }
          }

        val pSumValuePairs = (accumGlobal.pSums, featureValues).zipped.toTraversable

        val newPSums = parallelGroupSizes match {
          case Nil => calcAux(pSumValuePairs)
          case _ => pSumValuePairs.grouped(parallelGroupSizes).toArray.par.flatMap(calcAux).arrayseq
        }
        PersonIterativeAccumGlobal(newSumSqSums, newPSums.toSeq, accumGlobal.count + 1)
    }

  override def postSink(parallelGroupSizes: Seq[Int]) = { globalAccum: INTER =>
    val accums = globalAccumToAccums(globalAccum)
    PearsonCorrelationCalc.postSink(parallelGroupSizes)(accums)
  }

  private def globalAccumToAccums(
    globalAccum: PersonIterativeAccumGlobalArray
  ): Seq[Seq[PersonIterativeAccum]] = {
    logger.info("Converting the global streamed accumulator to the partial ones.")
    (globalAccum.pSums, globalAccum.sumSqSums).zipped.map { case (rowPSums, (sum1, sqSum1)) =>
      (rowPSums, globalAccum.sumSqSums).zipped.map { case (pSum, (sum2, sqSum2)) =>
        PersonIterativeAccum(sum1, sum2, sqSum1, sqSum2, pSum, globalAccum.count)
      }
    }
  }

  @Deprecated
  private def globalAccumToAccumsOld(
    globalAccum: PersonIterativeAccumGlobal
  ): Seq[Seq[PersonIterativeAccum]] = {
    logger.info("Converting the global streamed accumulator to the partial ones.")
    (globalAccum.pSums, globalAccum.sumSqSums).zipped.map { case (rowPSums, (sum1, sqSum1)) =>
      (rowPSums, globalAccum.sumSqSums).zipped.map { case (pSum, (sum2, sqSum2)) =>
        PersonIterativeAccum(sum1, sum2, sqSum1, sqSum2, pSum, globalAccum.count)
      }
    }
  }
}

case class PersonIterativeAccumGlobalArray(
  sumSqSums: Seq[(Double, Double)],
  pSums: Array[mutable.ArraySeq[Double]],
  count: Int
)

@Deprecated
case class PersonIterativeAccumGlobal(
  sumSqSums: Seq[(Double, Double)],
  pSums: Seq[Seq[Double]],
  count: Int
)