package scala

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import org.scalatest._
import services.stats.calc.{UniqueDistributionCountsCalc}

import scala.concurrent.Future
import scala.util.Random

class UniqueDistributionTest extends AsyncFlatSpec with Matchers {

  private val values1: Seq[Double] = Seq(0.5, 0.5, 1, 2, 0.1, 2, 7, 3, 5, 7, 0.5, 2)
  private val expectedResult1 = Seq(0.1 -> 1, 0.5 -> 3, 1.0 -> 1, 2.0 -> 3, 3.0 -> 1, 5.0 -> 1, 7.0 -> 2)

  private val values2 = Seq(None, Some("cat"), None, Some("dog"), Some("zebra"), Some("tiger"), Some("dog"), None, Some("dolphin"), Some("dolphin"), Some("cat"), Some("dolphin"))
  private val expectedResult2 = Seq(None -> 3, Some("cat") -> 2, Some("dog") -> 2, Some("dolphin") -> 3, Some("tiger") -> 1, Some("zebra") -> 1)

  private val randomInputSize = 1000

  private val doubleCalc = UniqueDistributionCountsCalc[Double]
  private val stringCalc = UniqueDistributionCountsCalc[String]

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  "Distributions" should "match the static example (double)" in {
    val inputs = values1.map(Some(_))
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    def checkResult(result: Traversable[(Option[Double], Int)]) = {
      result.size should be (expectedResult1.size)

      val sorted = result.toSeq.sortBy(_._1)

      sorted.zip(expectedResult1).foreach{ case ((Some(value1), count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (values1.size)
    }

    // standard calculation
    Future(doubleCalc.fun()(inputs)).map(checkResult)

    // streamed calculations
    doubleCalc.runSink((),())(inputSource).map(checkResult)
  }

  "Distributions" should "match the static example (string)" in {
    val inputSource = Source.fromIterator(() => values2.toIterator)

    def checkResult(result: Traversable[(Option[String], Int)]) = {
      result.size should be (expectedResult2.size)

      val sorted = result.toSeq.sortBy(_._1)

      sorted.zip(expectedResult2).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (values2.size)
    }

    // standard calculation
    Future(stringCalc.fun()(values2)).map(checkResult)

    // streamed calculations
    stringCalc.runSink((), ())(inputSource).map(checkResult)
  }

  "Distributions" should "match each other (double)" in {
    val inputs = for (_ <- 1 to randomInputSize) yield {
       if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(20).toDouble)
    }
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // standard calculation
    val protoResult = doubleCalc.fun()(inputs).toSeq.sortBy(_._1)

    def checkResult(result: Traversable[(Option[Double], Int)]) = {
      result.size should be (protoResult.size)

      val sorted = result.toSeq.sortBy(_._1)

      sorted.zip(protoResult).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (inputs.size)
    }

    // streamed calculations
    doubleCalc.runSink((), ())(inputSource).map(checkResult)
  }

  "Distributions" should "match each other (string)" in {
    val inputs = for (_ <- 1 to randomInputSize) yield {
      if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(20).toString)
    }
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // standard calculation
    val protoResult = stringCalc.fun()(inputs).toSeq.sortBy(_._1)

    def checkResult(result: Traversable[(Option[String], Int)]) = {
      result.size should be (protoResult.size)

      val sorted = result.toSeq.sortBy(_._1)

      sorted.zip(protoResult).foreach{ case ((value1, count1), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_._2).sum should be (inputs.size)
    }

    // streamed calculations
    stringCalc.runSink((), ())(inputSource).map(checkResult)
  }
}