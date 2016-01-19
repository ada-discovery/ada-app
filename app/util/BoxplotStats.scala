package util

/**
  * TODO: Rename this, the name is quite meaningless!
  * Utility functions for boxplots: median, standard deviation and etc
  */
package object BoxplotStats {

  /**
    * Calculate quantiles for boxplots.
    * Generation is meant for Tukey boxplots.
    *
    * @param elements sequence of elements.
    * @return 5-value tuple with (lower 1.5 IQR whisker, lower quantile, median, upper quantile, upper 1.5 IQR whisker)
    */
  def quantiles(elements: Seq[Any]) : (Double, Double, Double, Double, Double) =
  {
    val length = elements.length
    //TODO fix input type instead of using this
    val elementsD = elements.map(e => e.toString.toDouble)

    // median
    val sorted = elementsD.sortWith(_ < _)
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

}
