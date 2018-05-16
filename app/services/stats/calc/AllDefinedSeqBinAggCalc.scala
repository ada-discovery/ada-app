package services.stats.calc

import akka.stream.scaladsl.Flow
import services.stats.{Calculator, CalculatorTypePack}

import scala.collection.mutable
import util.{GroupMapList, crossProduct}

trait AllDefinedSeqBinAggCalcTypePack[ACCUM] extends CalculatorTypePack {
  type IN = Seq[Double]
  type OUT = Traversable[(Seq[BigDecimal], Option[Double])]
  type INTER = mutable.ArraySeq[ACCUM]
  type OPT = Seq[NumericDistributionOptions]
  type FLOW_OPT = Seq[NumericDistributionFlowOptions]
  type SINK_OPT = FLOW_OPT
}

private[calc] trait AllDefinedSeqBinAggCalc[ACCUM] extends Calculator[AllDefinedSeqBinAggCalcTypePack[ACCUM]] with NumericDistributionCountsHelper {

  protected def calcAgg(values: Traversable[Double]): Option[Double]

  protected def initAccum: ACCUM

  protected def updateAccum(
    accum: ACCUM,
    value: Double
  ): ACCUM

  protected def accumToAgg(accum: ACCUM): Option[Double]

  override def fun(options: Seq[NumericDistributionOptions]) = { values =>
    if (values.nonEmpty) {

      val infos = options.zipWithIndex.map { case (option, index) =>
        val oneDimValues = values.map(_(index))
        val min = oneDimValues.min
        val max = oneDimValues.max

        val stepSize = calcStepSize(option.binCount, min, max, option.specialBinForMax)

        val minBg = BigDecimal(min)

        SingleDimBinInfo(minBg, max, option.binCount, stepSize)
      }

      val indecesValues = values.map { values =>
        val actualValue = values.last

        val indeces = infos.zip(values).map { case (info, value) =>
          calcBucketIndex(info.stepSize, info.binCount, info.min, info.max)(value)
        }

        (indeces, actualValue)
      }

      val indecesMeanMap = indecesValues.toGroupMap.map { case (indeces, values) =>
        (indeces, calcAgg(values))
      }

      val allIndeces = crossProduct(infos.map { info => (0 until info.binCount)}.toList )

      allIndeces.map { indeces =>
        val seqIndeces = indeces.toSeq
        val mean = indecesMeanMap.get(seqIndeces).flatten

        val values = seqIndeces.zip(infos).map { case (index, info) => info.min + (index * info.stepSize) }
        (values, mean)
      }
    } else
      Seq[(Seq[BigDecimal], Option[Double])]()
  }

  override def flow(options: FLOW_OPT) = {
    val infos = options.map { option =>
      val stepSize = calcStepSize(option.binCount, option.min, option.max, option.specialBinForMax)

      val minBg = BigDecimal(option.min)

      SingleDimBinInfo(minBg, option.max, option.binCount, stepSize)
    }

    val gridSize = options.map(_.binCount).fold(1){_*_}

    Flow[IN].fold[INTER](
      mutable.ArraySeq(Seq.fill(gridSize)(initAccum) :_*)
    ) { case (sumCounts, values) =>

      val combinedIndex = infos.zip(values).foldLeft(0) { case (cumIndex, (info, value)) =>
        val index = calcBucketIndex(
          info.stepSize, info.binCount, info.min, info.max)(value
        )

        (cumIndex * info.binCount) + index
      }

      val actualValue = values.last

      val accum = sumCounts(combinedIndex)
      sumCounts.update(combinedIndex, updateAccum(accum, actualValue))

      sumCounts
    }
  }

  override def postFlow(options: SINK_OPT) = { sumCounts =>
    val infos = options.map { option =>
      val stepSize = calcStepSize(option.binCount, option.min, option.max, option.specialBinForMax)

      val minBg = BigDecimal(option.min)

      SingleDimBinInfo(minBg, option.max, option.binCount, stepSize)
    }

    val allIndeces = crossProduct(infos.map { info => (0 until info.binCount) }.toList)

    allIndeces.map { indeces =>
      val seqIndeces = indeces.toSeq

      val combinedIndex = infos.zip(seqIndeces).foldLeft(0){ case (cumIndex, (info, index)) =>
        (cumIndex * info.binCount) + index
      }
      val accum = sumCounts(combinedIndex)

      val values = seqIndeces.zip(infos).map { case (index, info) =>
        info.min + (index * info.stepSize)
      }

      (values, accumToAgg(accum))
    }
  }
}

case class SingleDimBinInfo(
  min: BigDecimal,
  max: Double,
  binCount: Int,
  stepSize: BigDecimal
)