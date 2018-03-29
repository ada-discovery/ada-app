package services.stats.calc

import akka.stream.scaladsl.{Flow, Keep, Sink}
import services.stats.Calculator
import StandardizationCalcIOTypes._

object StandardizationCalcIOTypes {
  type IN = Seq[Option[Double]]
  type OUT = Traversable[Seq[Option[Double]]]
  type INTER = OUT
  type OPT = Seq[(Double, Double)]
}

object StandardizationCalc extends Calculator[IN, OUT, INTER, OPT, OPT, Unit] {

  override def fun(options: OPT) = _.map(standardizeRow(options))

  override def sink(options: OPT) =
    Flow[IN].map(standardizeRow(options)).toMat(Sink.seq)(Keep.right)

  private def standardizeRow(
    options: OPT)(
    row: IN
  ) =
    row.zip(options).map { case (value, (shift, norm)) =>
      value.map ( value => (value - shift) / norm)
    }

  override def postSink(o: Unit) = identity
}