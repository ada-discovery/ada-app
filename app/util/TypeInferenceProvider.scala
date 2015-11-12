package util

class TypeInferenceProvider {

  private def isBoolean(valuesWoNA : Set[String]) =
    isTextBoolean(valuesWoNA) || isNumberBoolean(valuesWoNA)

  private def isTextBoolean(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => textBooleanValues.contains(s.toLowerCase))

  private def isNumberBoolean(valuesWoNA : Set[String]) =
    valuesWoNA.forall(s => numBooleanValues.contains(s)) //    isNumber(valuesWoNA) && valuesWoNA.size <= 2

  private def isEnum(freqsWoNa : Seq[Double]) =
    freqsWoNa.size < enumValuesThreshold && (freqsWoNa.sum / freqsWoNa.size) > enumFrequencyThreshold

  private def isNumberEnum(valuesWoNA : Set[String], freqsWoNa : Seq[Double]) =
    isNumber(valuesWoNA) && isEnum(freqsWoNa)

  private def isTextEnum(valuesWoNA : Set[String], freqsWoNa : Seq[Double]) =
    isEnum(freqsWoNa) && valuesWoNA.exists(_.exists(_.isLetter))
}
