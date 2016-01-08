package util

/**
  * TODO: Rename this, the name is quite meaningless!
  * Utility functions for boxplots: median, standard deviation and etc
  */
package object BoxplotStats {

  /**
    * Calculate quantiles for boxplots
    * @param elements sequence of elements
    * @return triple with lower quantile, median and upper quantile
    */
  def quartiles(elements: Seq[Double]) : (Double, Double, Double) =
  {
    // median
    val sorted = elements.sortWith(_ < _)
    val (lowerSplit, upperSplit) = sorted.splitAt(elements.length / 2)
    val median = upperSplit.head

    // upper quartile
    val (qu, _) = lowerSplit.splitAt(elements.length / 4)
    val upperQ = qu.head

    // lower quartile
    val (ql, _) = upperSplit.splitAt(elements.length / 4)
    val lowerQ = ql.head

    (lowerQ, median, upperQ)
  }

}
