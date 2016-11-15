package util

import scala.collection.mutable.{Map => MMap}

/**
  * Utility functions for boxplots: median, standard deviation and etc
  */
package object BasicStats {

  case class Quantiles[T](
    lowerWhisker: T,
    lowerQuantile: T,
    median: T,
    upperQuantile: T,
    upperWhisker: T
  )

  /**
    *
    * Calculate quantiles for boxplots.
    * Generation is meant for Tukey boxplots.
    *
    * @param elements sequence of elements.
    * @return 5-value tuple with (lower 1.5 IQR whisker, lower quantile, median, upper quantile, upper 1.5 IQR whisker)
    */
  def quantiles[T](elements: Seq[T])(implicit num: Numeric[T]): Option[Quantiles[T]] =
    elements.headOption.map { _ =>
      val length = elements.length
      val sorted = elements.sorted

      // median
      val median = sorted(length / 2)

      // upper quartile
      val upperQuantile = sorted(3 * length / 4)

      // lower quartile
      val lowerQuantile = sorted(length / 4)

      val iqr = num.toDouble(num.minus(upperQuantile, lowerQuantile))

      val upperWhiskerValue = num.toDouble(upperQuantile) + 1.5 * iqr
      val lowerWhiskerValue = num.toDouble(lowerQuantile) - 1.5 * iqr

      val lowerWhisker = sorted.find(value => num.toDouble(value) >= lowerWhiskerValue).getOrElse(sorted.last)
      val upperWhisker = sorted.reverse.find(value => num.toDouble(value) <= upperWhiskerValue).getOrElse(sorted.head)

      Quantiles(lowerWhisker, lowerQuantile, median, upperQuantile, upperWhisker)
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