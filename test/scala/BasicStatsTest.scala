package scala

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.scalatest._
import services.stats.calc.{BasicStatsCalc, BasicStatsResult}
import services.stats.CalculatorHelper._

import scala.concurrent.Future
import scala.util.Random

class BasicStatsTest extends AsyncFlatSpec with Matchers with ExtraMatchers {

  private val values: Seq[Option[Double]] = Seq(None, Some(0.5), Some(2), Some(-3.5), None, Some(8.9), Some(4.2), Some(8.1), Some(0), Some(-1))

  private val variance = 22.495 - 2.4 * 2.4
  private val expectedResult = BasicStatsResult(
    -3.5, 8.9, 2.4, variance, Math.sqrt(variance), variance * 8 / 7, Math.sqrt(variance * 8 / 7), 8, 2
  )

  private val randomInputSize = 1000
  private val precision = 0.00000001

  private val calc = BasicStatsCalc

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  "Basic stats" should "match the static example" in {
    val inputSource = Source.fromIterator(() => values.toIterator)

    def checkResult(result: Option[BasicStatsResult]) = {
      result should not be (None)
      val resultDefined = result.get

      resultDefined.min should be (expectedResult.min)
      resultDefined.max should be (expectedResult.max)
      resultDefined.mean shouldBeAround (expectedResult.mean, precision)
      resultDefined.variance shouldBeAround (expectedResult.variance, precision)
      resultDefined.standardDeviation shouldBeAround (expectedResult.standardDeviation, precision)
      resultDefined.sampleVariance shouldBeAround (expectedResult.sampleVariance, precision)
      resultDefined.sampleStandardDeviation shouldBeAround (expectedResult.sampleStandardDeviation, precision)
      resultDefined.definedCount should be (expectedResult.definedCount)
      resultDefined.undefinedCount should be (expectedResult.undefinedCount)
    }

    // standard calculation
    Future(calc.fun_(values)).map(checkResult)

    // streamed calculations
    calc.runFlow_(inputSource).map(checkResult)
  }

  "Basic stats" should "match each other" in {
    val inputs = for (_ <- 1 to randomInputSize) yield {
      if (Random.nextDouble < 0.2) None else Some(Random.nextDouble())
    }
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // standard calculation
    val protoResult = calc.fun_(inputs).get

    def checkResult(result: Option[BasicStatsResult]) = {
      result should not be (None)

      val resultDefined = result.get

      resultDefined.min should be (protoResult.min)
      resultDefined.max should be (protoResult.max)
      resultDefined.mean shouldBeAround (protoResult.mean, precision)
      resultDefined.variance shouldBeAround (protoResult.variance, precision)
      resultDefined.standardDeviation shouldBeAround (protoResult.standardDeviation, precision)
      resultDefined.definedCount should be (protoResult.definedCount)
      resultDefined.undefinedCount should be (protoResult.undefinedCount)
    }

    // streamed calculations
    calc.runFlow_(inputSource).map(checkResult)
  }
}