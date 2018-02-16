package scala

import akka.stream.scaladsl.Source
import dataaccess.FieldTypeHelper
import models.{Count, Field, FieldTypeId}
import org.scalatest._
import services.stats.StatsService
import services.stats.calc.{PearsonCorrelationCalc, UniqueDistributionCountsCalc}

import scala.concurrent.Future
import scala.util.Random

class DistributionTest extends AsyncFlatSpec with Matchers {

  private val values1: Seq[Double] = Seq(0.5, 0.5, 1, 2, 0.1, 2, 7, 3, 5, 7, 0.5, 2)
  private val expectedResult1 = Seq(0.1 -> 1, 0.5 -> 3, 1.0 -> 1, 2.0 -> 3, 3.0 -> 1, 5.0 -> 1, 7.0 -> 2)

  private val values2 = Seq(None, Some("cat"), None, Some("dog"), Some("zebra"), Some("tiger"), Some("dog"), None, Some("dolphin"), Some("dolphin"), Some("cat"), Some("dolphin"))
  private val expectedResult2 = Seq(None -> 3, Some("cat") -> 2, Some("dog") -> 2, Some("dolphin") -> 3, Some("tiger") -> 1, Some("zebra") -> 1)

  private val randomInputSize = 1000

  private val injector = TestApp.apply.injector
  private val statsService = injector.instanceOf[StatsService]

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val doubleCalc = UniqueDistributionCountsCalc[Double]
  private val doubleField = Field("xxx", None, FieldTypeId.Double)
  private val doubleFieldType = ftf(doubleField.fieldTypeSpec).asValueOf[Double]

  private val stringCalc = UniqueDistributionCountsCalc[String]
  private val stringField = Field("xxx", None, FieldTypeId.String)
  private val stringFieldType = ftf(stringField.fieldTypeSpec).asValueOf[String]

  "Distributions" should "match the static example (double)" in {
    val inputs = values1.map(Some(_))
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    def checkResult(result: Traversable[Count[String]]) = {
      result.size should be (expectedResult1.size)

      val sorted = result.toSeq.sortBy(_.value)

      sorted.zip(expectedResult1).foreach{ case (Count(value1, count1, _), (value2, count2)) =>
        value1 should be (value2.toString)
        count1 should be (count2)
      }

      result.map(_.count).sum should be (values1.size)
    }

    // standard calculation
    Future(doubleCalc.fun()(inputs)).map { counts =>
      val results = statsService.createStringCounts(counts, doubleFieldType)
      checkResult(results)
    }

    // streamed calculations
    statsService.calcDistributionCountsStreamed[Double](inputSource, doubleField).map(checkResult)
  }

  "Distributions" should "match the static example (string)" in {
    val inputSource = Source.fromIterator(() => values2.toIterator)

    def checkResult(result: Traversable[Count[String]]) = {
      result.size should be (expectedResult2.size)

      val sorted = result.toSeq.sortBy(_.value)

      sorted.zip(expectedResult2).foreach{ case (Count(value1, count1, _), (value2, count2)) =>
        value1 should be (value2.getOrElse("Undefined"))
        count1 should be (count2)
      }

      result.map(_.count).sum should be (values2.size)
    }

    // standard calculation
    Future(stringCalc.fun()(values2)).map { counts =>
      val results = statsService.createStringCounts(counts, stringFieldType)
      checkResult(results)
    }

    // streamed calculations
    statsService.calcDistributionCountsStreamed[String](inputSource, stringField).map(checkResult)
  }

  "Distributions" should "match each other (double)" in {
    val inputs = for (_ <- 1 to randomInputSize) yield {
       if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(20).toDouble)
    }
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // standard calculation
    val counts = doubleCalc.fun()(inputs)
    val protoResult = statsService.createStringCounts(counts, doubleFieldType).toSeq.sortBy(_.value)

    def checkResult(result: Traversable[Count[String]]) = {
      result.size should be (protoResult.size)

      val sorted = result.toSeq.sortBy(_.value)

      sorted.zip(protoResult).foreach{ case (Count(value1, count1, _), Count(value2, count2, _)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_.count).sum should be (inputs.size)
    }

    // streamed calculations
    statsService.calcDistributionCountsStreamed[Double](inputSource, doubleField).map(checkResult)
  }

  "Distributions" should "match each other (string)" in {
    val inputs = for (_ <- 1 to randomInputSize) yield {
      if (Random.nextDouble() < 0.2) None else Some(Random.nextInt(20).toString)
    }
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    // standard calculation
    val counts = stringCalc.fun()(inputs)
    val protoResult = statsService.createStringCounts(counts, stringFieldType).toSeq.sortBy(_.value)

    def checkResult(result: Traversable[Count[String]]) = {
      result.size should be (protoResult.size)

      val sorted = result.toSeq.sortBy(_.value)

      sorted.zip(protoResult).foreach{ case (Count(value1, count1, _), Count(value2, count2, _)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_.count).sum should be (inputs.size)
    }

    // streamed calculations
    statsService.calcDistributionCountsStreamed[String](inputSource, stringField).map(checkResult)
  }
}