package scala

import akka.stream.scaladsl.Source
import dataaccess.FieldTypeHelper
import models.{Count, Field, FieldTypeId}
import org.scalatest._
import services.stats.StatsService
import services.stats.calc.{NumericDistributionCountsCalc, NumericDistributionOptions, NumericDistributionSinkOptions, UniqueDistributionCountsCalc}

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

  private val injector = TestApp.apply.injector
  private val statsService = injector.instanceOf[StatsService]

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val doubleCalc = NumericDistributionCountsCalc[Double]
  private val doubleField = Field("xxx", None, FieldTypeId.Double)
  private val doubleFieldType = ftf(doubleField.fieldTypeSpec).asValueOf[Double]

  private val intCalc = NumericDistributionCountsCalc[Long]
  private val intField = Field("xxx", None, FieldTypeId.Integer)
  private val intFieldType = ftf(intField.fieldTypeSpec).asValueOf[Long]

  "Distributions" should "match the static example (double)" in {
    val inputs = values1.map(Some(_))
    val inputSource = Source.fromIterator(() => inputs.toIterator)

    def checkResult(result: Traversable[Count[_]]) = {
      result.size should be (expectedResult1.size)

      result.toSeq.zip(expectedResult1).foreach{ case (Count(value1, count1, _), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_.count).sum should be (values1.size)
    }

    // standard calculation
    val standardOptions = NumericDistributionOptions[Double](columnCount1)
    Future(doubleCalc.fun(standardOptions)(inputs)).map { counts =>
      val results = statsService.convertNumericalCounts(counts)
      checkResult(results)
    }

    // streamed calculations
    val streamOptions = NumericDistributionSinkOptions(columnCount1, values1.min, values1.max)
    statsService.calcNumericDistributionCountsStreamed[Double](inputSource, streamOptions).map(checkResult)
  }

  "Distributions" should "match the static example (int/long)" in {
    val inputSource = Source.fromIterator(() => values2.toIterator)

    def checkResult(result: Traversable[Count[_]]) = {
      result.size should be (expectedResult2.size)

      result.toSeq.zip(expectedResult2).foreach{ case (Count(value1, count1, _), (value2, count2)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_.count).sum should be (values2.flatten.size)
    }

    // standard calculation
    val standardOptions = NumericDistributionOptions[Long](columnCount2, None, None, true)
    Future(intCalc.fun(standardOptions)(values2)).map { counts =>
      val results = statsService.convertNumericalCounts(counts)
      checkResult(results)
    }

    // streamed calculations
    val streamOptions = NumericDistributionSinkOptions(columnCount2, values2.flatten.min, values2.flatten.max, true)
    statsService.calcNumericDistributionCountsStreamed[Long](inputSource, streamOptions).map(checkResult)
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
    val counts = doubleCalc.fun(standardOptions)(inputs)
    val protoResult = statsService.convertNumericalCounts(counts)

    def checkResult(result: Traversable[Count[_]]) = {
      result.size should be (protoResult.size)

      result.toSeq.zip(protoResult).foreach{ case (Count(value1, count1, _), Count(value2, count2, _)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_.count).sum should be (flattenedInputs.size)
    }

    // streamed calculations

    val streamOptions = NumericDistributionSinkOptions(columnCount, flattenedInputs.min, flattenedInputs.max)
    statsService.calcNumericDistributionCountsStreamed[Double](inputSource, streamOptions).map(checkResult)
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
    val counts = intCalc.fun(standardOptions)(inputs)
    val protoResult = statsService.convertNumericalCounts(counts)

    def checkResult(result: Traversable[Count[_]]) = {
      result.size should be (protoResult.size)

      result.toSeq.zip(protoResult).foreach{ case (Count(value1, count1, _), Count(value2, count2, _)) =>
        value1 should be (value2)
        count1 should be (count2)
      }

      result.map(_.count).sum should be (flattenedInputs.size)
    }

    // streamed calculations

    val streamOptions = NumericDistributionSinkOptions(columnCount, flattenedInputs.min, flattenedInputs.max)
    statsService.calcNumericDistributionCountsStreamed[Long](inputSource, streamOptions).map(checkResult)
  }
}