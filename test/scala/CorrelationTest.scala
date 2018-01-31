package runnables

import javax.inject.Inject

import services.StatsService

class CorrelationTest @Inject() (statsService: StatsService) extends App {

  private val xs = Seq(0.5, 0.7, 1.2, 6.3, 0.1, 0.4, 0.7, -1.2, 3, 4.2, 5.7, 4.2, 8.1)
  private val ys = Seq(0.5, 0.4, 0.4, 0.4, -1.2, 0.8, 0.23, 0.9, 2, 0.1, -4.1, 3, 4)
  private val expectedResult = 0.1725

  val result = statsService.calcPearsonCorrelations(xs.zip(ys).map{ case (a,b) => Seq(Some(a),Some(b))})
  assert(result.equals(expectedResult), s"$expectedResult expected but got $result")
}