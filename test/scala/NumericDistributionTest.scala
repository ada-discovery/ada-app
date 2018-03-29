package scala

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.scalatest._
import services.stats.calc.{NumericDistributionCountsCalc, NumericDistributionOptions, NumericDistributionSinkOptions}

import scala.concurrent.Future
import scala.util.Random

class NumericDistributionTest extends AsyncFlatSpec with Matchers {

  private val values1: Seq[Double] = Seq(0.5, 0.5, 1.5, 2, 0.6, 2.4, 2.6, 3, 5, 7.5, 1.1, 2)
  private val expectedResult1 = Seq(0.5 -> 4, 1.5 -> 4, 2.5 -> 2, 3.5 -> 0, 4.5 -> 1, 5.5 -> 0, 6.5 -> 1)
  private val columnCount1 = 7

  private val values2: Seq[Option[Long]] = Seq(Some(1), None, Some(1), Some(3), Some(2), None, Some(3), Some(2), Some(2), Some(3), Some(5), Some(7), Some(4), Some(2), None, None)
  private val expectedResult2 = Seq(1 -> 2, 2 -> 4, 3 -> 3, 4 -> 1, 5 -> 1, 6 -> 0, 7 -> 1)
  private val columnCount2 = 7

  private val randomInputSize = 1000

  private val doubleCalc = NumericDistributionCountsCalc[Double]
  private val intCalc = NumericDistributionCountsCalc[Long]

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  "Distributions" should "match the static example (double)" in {
    val inputs = values1.map(Some(_))
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    def checkResult(result: Traversable[(BigDecimal, Int)]) = {
      result.size should be (expectedResult1.size)

      result.toSeq.zip(expectedResult1).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (values1.size)
    }

    // standard calculation
    val standardOptions = NumericDistributionOptions[Double](columnCount1)
    Future(doubleCalc.fun(standardOptions)(inputs)).map(checkResult)

    // streamed calculations
    val streamOptions = NumericDistributionSinkOptions(columnCount1, values1.min, values1.max)
    doubleCalc.runSink(streamOptions, streamOptions)(inputSource).map(checkResult)
  }

  "Distributions" should "match the static example (int/long)" in {
    val inputSource = Source.fromIterator(() => values2.toIterator)

    def checkResult(result: Traversable[(BigDecimal, Int)]) = {
      result.size should be (expectedResult2.size)

      result.toSeq.zip(expectedResult2).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (values2.flatten.size)
    }

    // standard calculation
    val standardOptions = NumericDistributionOptions[Long](columnCount2, None, None, true)
    Future(intCalc.fun(standardOptions)(values2)).map(checkResult)

    // streamed calculations
    val streamOptions = NumericDistributionSinkOptions(columnCount2, values2.flatten.min, values2.flatten.max, true)
    intCalc.runSink(streamOptions, streamOptions)(inputSource).map(checkResult)
  }

  "Distributions" should "match each other (double)" in {
    val inputs = for (_ <- 1 to randomInputSize) yield {
       if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(20).toDouble)
    }
    val flattenedInputs = inputs.flatten
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    val columnCount = 30

    // standard calculation
    val standardOptions = NumericDistributionOptions[Double](columnCount)
    val protoResult = doubleCalc.fun(standardOptions)(inputs).toSeq

    def checkResult(result: Traversable[(BigDecimal, Int)]) = {
      result.size should be (protoResult.size)

      result.toSeq.zip(protoResult).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (flattenedInputs.size)
    }

    // streamed calculations

    val streamOptions = NumericDistributionSinkOptions(columnCount, flattenedInputs.min, flattenedInputs.max)
    doubleCalc.runSink(streamOptions, streamOptions)(inputSource).map(checkResult)
  }

  "Distributions" should "match each other (int/long)" in {
    val inputs = for (_ <- 1 to randomInputSize) yield {
      if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(20).toLong)
    }
    val flattenedInputs = inputs.flatten
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    val columnCount = 15

    // standard calculation
    val standardOptions = NumericDistributionOptions[Long](columnCount)
    val protoResult = intCalc.fun(standardOptions)(inputs)

    def checkResult(result: Traversable[(BigDecimal, Int)]) = {
      result.size should be (protoResult.size)

      result.toSeq.zip(protoResult.toSeq).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (flattenedInputs.size)
    }

    // streamed calculations

    val streamOptions = NumericDistributionSinkOptions(columnCount, flattenedInputs.min, flattenedInputs.max)
    intCalc.runSink(streamOptions, streamOptions)(inputSource).map(checkResult)
  }
}