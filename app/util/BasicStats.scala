package util

import scala.collection.mutable.{Map => MMap}

/**
  * Utility functions for boxplots: median, standard deviation and etc
  */
package object BasicStats {

  case class Quantiles[T <% Ordered[T]](
    lowerWhisker: T,
    lowerQuantile: T,
    median: T,
    upperQuantile: T,
    upperWhisker: T
  ) {
    def ordering = implicitly[Ordering[T]]
    def toSeq: Seq[T] = Seq(lowerWhisker, lowerQuantile, median, upperQuantile, upperWhisker)
  }

  /**
    *
    * Calculate quantiles for boxplots.
    * Generation is meant for Tukey boxplots.
    *
    * @param elements sequence of elements.
    * @return 5-value tuple with (lower 1.5 IQR whisker, lower quantile, median, upper quantile, upper 1.5 IQR whisker)
    */
  def quantiles[T: Ordering](
    elements: Seq[T],
    toDouble: T => Double
  ): Option[Quantiles[T]] =
    elements.headOption.map { _ =>
      val length = elements.length
      val sorted = elements.sorted

      // median
      val median = sorted(length / 2)

      // upper quartile
      val upperQuantile = sorted(3 * length / 4)

      // lower quartile
      val lowerQuantile = sorted(length / 4)

      val upperQuantileDouble = toDouble(upperQuantile)
      val lowerQuantileDouble = toDouble(lowerQuantile)
      val iqr = upperQuantileDouble - lowerQuantileDouble

      val upperWhiskerValue = upperQuantileDouble + 1.5 * iqr
      val lowerWhiskerValue = lowerQuantileDouble - 1.5 * iqr

      val lowerWhisker = sorted.find(value => toDouble(value) >= lowerWhiskerValue).getOrElse(sorted.last)
      val upperWhisker = sorted.reverse.find(value => toDouble(value) <= upperWhiskerValue).getOrElse(sorted.head)

      Quantiles(lowerWhisker, lowerQuantile, median, upperQuantile, upperWhisker)
  }

  def pearsonCorrelation(
    values: Traversable[Seq[Option[Double]]]
  ): Seq[Seq[Option[Double]]] = {
    val elementsCount = if (values.nonEmpty) values.head.size else 0

    def calc(index1: Int, index2: Int) = {
      val els = (values.map(_ (index1)).toSeq, values.map(_ (index2)).toSeq).zipped.map {
        case (el1: Option[Double], el2: Option[Double]) =>
          if ((el1.isDefined) && (el2.isDefined)) {
            Some((el1.get, el2.get))
          } else
            None
      }.flatten

      if (els.nonEmpty) {
        val length = els.size

        val mean1 = els.map(_._1).sum / length
        val mean2 = els.map(_._2).sum / length

        // sum up the squares
        val mean1Sq = els.map(_._1).foldLeft(0.0)(_ + Math.pow(_, 2)) / length
        val mean2Sq = els.map(_._2).foldLeft(0.0)(_ + Math.pow(_, 2)) / length

        // sum up the products
        val pMean = els.foldLeft(0.0) { case (accum, pair) => accum + pair._1 * pair._2 } / length

        // calculate the pearson score
        val numerator = pMean - mean1 * mean2

        val denominator = Math.sqrt(
          (mean1Sq - Math.pow(mean1, 2)) * (mean2Sq - Math.pow(mean2, 2))
        )
        if (denominator == 0)
          None
        else
          Some(numerator / denominator)
      } else
        None
    }

    (0 until elementsCount).par.map { i =>
      (0 until elementsCount).par.map { j =>
        if (i != j)
          calc(i, j)
        else
          Some(1d)
      }.toList
    }.toList
  }

  /**
    * Calculates mean value for statistical overview.
    *
    * @param elements Input elements.
    * @return Option containing mean. If mean can not be calculated, None.
    */
  def mean[T](
    elements: Seq[T])(
    implicit num: Numeric[T]
  ): Option[Double] =
    elements.headOption.map ( _ =>
      num.toDouble(elements.sum) / elements.length
    )
}