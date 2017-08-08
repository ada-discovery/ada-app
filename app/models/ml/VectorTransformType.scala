package models.ml

object VectorTransformType extends Enumeration {
  val Normalizer, StandardScaler, MinMaxScaler, MaxAbsScaler = Value
}
