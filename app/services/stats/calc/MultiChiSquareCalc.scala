package services.stats.calc

import akka.stream.scaladsl.{Flow, Source}
import services.stats.{Calculator, NoOptionsCalculatorTypePack}
import services.stats.CalculatorHelper._
import util.AkkaStreamUtil.unzipNFlowsAndApply

trait MultiChiSquareCalcTypePack[G, T] extends NoOptionsCalculatorTypePack {
  type IN = (Option[G], Seq[Option[T]])
  type OUT = Seq[Option[ChiSquareResult]]
  type INTER =  Seq[ChiSquareTestCalcTypePack[G, T]#INTER]
}

private class MultiChiSquareCalc[G, T] extends Calculator[MultiChiSquareCalcTypePack[G, T]] {

  private val coreCalc = ChiSquareTestCalc[G, T]

  override def fun(o: Unit) = { values: Traversable[IN] =>
    if (values.isEmpty || values.head._2.isEmpty)
      Nil
    else {
      val testsNum = values.head._2.size
      (0 to testsNum - 1).map { index =>
        val inputs = values.map(row => (row._1, row._2(index)))
        coreCalc.fun_(inputs)
      }
    }
  }

  override def flow(o: Unit) = {
    // create tuples for each value
    val tupleSeqFlow = Flow[IN].map { case (group, values) => values.map((group, _))}

    // apply core flow for a single chi-square test in parallel by splitting the flow
    val seqChiSquareFlow = (size: Int) => unzipNFlowsAndApply(size)(coreCalc.flow_)

    // since we need to know the number of features (seq size) we take the first element out, apply the flow and concat
    tupleSeqFlow.prefixAndTail(1).flatMapConcat { case (first, tail) =>
      val size = first.headOption.map(_.size).getOrElse(0)
      Source(first).concat(tail).via(seqChiSquareFlow(size))
    }
  }

  override def postFlow(o: Unit) =
    _.map(coreCalc.postFlow_(_))
}

object MultiChiSquareCalc {
  def apply[G, T]: Calculator[MultiChiSquareCalcTypePack[G, T]] = new MultiChiSquareCalc[G, T]
}