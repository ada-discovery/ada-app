package services.stats.calc

object EuclideanDistanceCalc extends DistanceCalc[Option[Double], (Int, Seq[Int]), Unit] {

  override protected def dist(
    el1: Option[Double],
    el2: Option[Double]
  ) =
    (el1, el2).zipped.headOption.map { case (value1, value2) =>
      val diff = value1 - value2
      diff * diff
    }

  override protected def processSum(sum: Double) = Math.sqrt(sum)

  override protected def featuresNumAndGroupSizes = identity
}

object AllDefinedEuclideanDistanceCalc extends DistanceCalc[Double, (Int, Seq[Int]), Unit] {

  override protected def dist(
    value1: Double,
    value2: Double
  ) = {
    val diff = value1 - value2
    Some(diff * diff)
  }

  override protected def processSum(sum: Double) = Math.sqrt(sum)

  override protected def featuresNumAndGroupSizes = identity
}