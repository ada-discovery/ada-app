package controllers

@Deprecated
object StudyRouter extends Enumeration {
  case class StudyRouterHolder(order: Int, name: String, dataSetRouter: DataSetRouter, dictionaryRouter: DictionaryRouter) extends super.Val
  implicit def valueToHolder(x: Value) = x.asInstanceOf[StudyRouterHolder]

  val LuxPark = StudyRouterHolder(
    0,
    "LuxPark",
    DataSetRouter("luxpark"),
    DictionaryRouter("luxpark")
  )

  val DeNoPaCuratedBaseline = StudyRouterHolder(
    1,
    "DeNoPa Curated Baseline",
    DataSetRouter("denopa-curated-baseline"),
    DictionaryRouter("denopa-curated-baseline")
  )

  val DeNoPaCuratedFirstVisit = StudyRouterHolder(
    2,
    "DeNoPa Curated First Visit",
    DataSetRouter("denopa-curated-firstvisit"),
    DictionaryRouter("denopa-curated-firstvisit")
  )

  val DeNoPaBaseline = StudyRouterHolder(
    3,
    "DeNoPa Baseline",
    DataSetRouter("denopa-baseline"),
    DictionaryRouter("denopa-baseline")
  )

  val DeNoPaFirstVisit = StudyRouterHolder(
    4,
    "DeNoPa First Visit",
    DataSetRouter("denopa-firstvisit"),
    DictionaryRouter("denopa-firstvisit")
  )
}