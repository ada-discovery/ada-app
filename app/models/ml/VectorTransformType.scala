package models.ml

object VectorTransformType extends Enumeration {
  val L1Normalizer, L2Normalizer, StandardScaler, MinMaxZeroOneScaler, MinMaxPlusMinusOneScaler, MaxAbsScaler = Value
}
