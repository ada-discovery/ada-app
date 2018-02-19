import akka.stream.scaladsl.Source
import org.scalatest._
import services.stats.StatsService
import services.stats.calc.PearsonCorrelationCalc

import scala.concurrent.Future
import scala.util.Random

class CorrelationTest extends AsyncFlatSpec with Matchers {

  private val xs = Seq(0.5, 0.7, 1.2, 6.3, 0.1, 0.4, 0.7, -1.2, 3, 4.2, 5.7, 4.2, 8.1)
  private val ys = Seq(0.5, 0.4, 0.4, 0.4, -1.2, 0.8, 0.23, 0.9, 2, 0.1, -4.1, 3, 4)
  private val expectedResult = 0.1725323796730674

  private val randomInputSize = 100
  private val randomFeaturesNum = 25

  private val injector = TestApp.apply.injector
  private val statsService = injector.instanceOf[StatsService]
  private val pearsonCorrelationCalc = PearsonCorrelationCalc.fun()

  "Correlations" should "match the static example" in {
    val inputs = xs.zip(ys).map{ case (a,b) => Seq(Some(a),Some(b))}
    val inputsAllDefined = xs.zip(ys).map{ case (a,b) => Seq(a, b)}
    val inputSource = Source.fromIterator(() => inputs.toIterator)
    val inputSourceAllDefined = Source.fromIterator(() => inputsAllDefined.toIterator)

    def checkResult(result: Seq[Seq[Option[Double]]]) = {
      result.size should be (2)
      result.map(_.size should be (2))
      result(0)(0).get should be (1d)
      result(1)(1).get should be (1d)
      result(0)(1).get should be (expectedResult)
      result(1)(0).get should be (expectedResult)
    }

    val featuresNum = inputs.head.size

    // standard calculation
    Future(pearsonCorrelationCalc(inputs)).map(checkResult)

    // streamed calculations
    statsService.calcPearsonCorrelationsStreamed(inputSource, featuresNum, None).map(checkResult)

    // streamed calculations with an all-values-defined optimization
    statsService.calcPearsonCorrelationsAllDefinedStreamed(inputSourceAllDefined, featuresNum, None).map(checkResult)

    // parallel streamed calculations
    statsService.calcPearsonCorrelationsStreamed(inputSource, featuresNum, Some(4)).map(checkResult)

    // parallel streamed calculations with an all-values-defined optimization
    statsService.calcPearsonCorrelationsAllDefinedStreamed(inputSourceAllDefined, featuresNum, Some(4)).map(checkResult)
  }

  "Correlations" should "match each other" in {
    val inputs = for (_ <- 1 to randomInputSize) yield {
      for (_ <- 1 to randomFeaturesNum) yield
       Some((Random.nextDouble() * 2) - 1)
    }
    val inputsAllDefined = inputs.map(_.map(_.get))
    val inputSource = Source.fromIterator(() => inputs.toIterator)
    val inputSourceAllDefined = Source.fromIterator(() => inputsAllDefined.toIterator)

    // standard calculation
    val protoResult = pearsonCorrelationCalc(inputs)

    def checkResult(result: Seq[Seq[Option[Double]]]) = {
      result.map(_.size should be (randomFeaturesNum))
      for (i <- 0 to randomFeaturesNum - 1) yield
        result(i)(i).get should be (1d)

      result.zip(protoResult).map { case (rowCor1, rowCor2) =>
        rowCor1.zip(rowCor2).map { case (cor1, cor2) =>
          cor1 should be (cor2)
        }
      }
      result.size should be (randomFeaturesNum)
    }

    // streamed calculations
    statsService.calcPearsonCorrelationsStreamed(inputSource, randomFeaturesNum, None).map(checkResult)

    // streamed calculations with an all-values-defined optimization
    statsService.calcPearsonCorrelationsAllDefinedStreamed(inputSourceAllDefined, randomFeaturesNum, None).map(checkResult)

    // parallel streamed calculations
    statsService.calcPearsonCorrelationsStreamed(inputSource, randomFeaturesNum, Some(4)).map(checkResult)

    // parallel streamed calculations with an all-values-defined optimization
    statsService.calcPearsonCorrelationsAllDefinedStreamed(inputSourceAllDefined, randomFeaturesNum, Some(4)).map(checkResult)
  }
}