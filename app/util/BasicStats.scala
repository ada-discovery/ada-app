package util

/**
  * TODO: Rename this, the name is quite meaningless!
  * Utility functions for boxplots: median, standard deviation and etc
  */
package object BasicStats {

  /**
    * TODO: use type Seq[Double] instead of Seq[Any]
    *
    * Calculate quantiles for boxplots.
    * Generation is meant for Tukey boxplots.
    *
    * @param elements sequence of elements.
    * @return 5-value tuple with (lower 1.5 IQR whisker, lower quantile, median, upper quantile, upper 1.5 IQR whisker)
    */
  def quantiles(elements: Seq[Any]) : (Double, Double, Double, Double, Double) =
  {
    val length = elements.length
    val elementsD = elements.map(e => e.toString.toDouble)

    // median
    val sorted = elementsD.sorted
    val (lowerSplit, upperSplit) = sorted.splitAt(length / 2)
    val median = upperSplit.head

    // upper quartile
    val (qu, _) = lowerSplit.splitAt(length / 4)
    val upperQuantile = qu.head

    // lower quartile
    val (ql, _) = upperSplit.splitAt(length / 4)
    val lowerQuantile = ql.head


    val iqr = upperQuantile - lowerQuantile
    val upperWhisker = median + 1.5 * iqr
    val lowerWhisker = median - 1.5 * iqr

    (lowerWhisker, lowerQuantile, median, upperQuantile, upperWhisker)
  }


  /**
    * TODO: highly inefficient due to casting.
    * Calculates mean value for statistical overview.
    *
    * @param elements Input elements.
    * @return Option containing mean. If mean can not be calculated, None.
    */
  def mean(elements: Seq[Any]) : Option[Double] =
    try {
      if (elements.nonEmpty) {
        val length = elements.length
        val sum = elements.map { x => x.toString.toDouble }.sum
        //val sum = elements.sum
        Some(sum / length)
      } else
        None
    } catch {
      case e: NumberFormatException => None
    }
}
