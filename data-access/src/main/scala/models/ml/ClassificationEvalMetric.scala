package models.ml

object ClassificationEvalMetric extends Enumeration {
  val f1, weightedPrecision, weightedRecall, accuracy, areaUnderROC, areaUnderPR = Value
}
