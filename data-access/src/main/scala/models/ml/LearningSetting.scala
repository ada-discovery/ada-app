package models.ml

case class LearningSetting(
  pcaDims: Option[Int] = None,
  trainingTestingSplit: Option[Double] = None,
  repetitions: Option[Int] = None,
  crossValidationFolds: Option[Int] = None
)