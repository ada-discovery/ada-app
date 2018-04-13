package services

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import dataaccess.{Criterion, FieldType, FieldTypeHelper}
import dataaccess.RepoTypes.JsonReadonlyRepo
import models._
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import services.stats.StatsService
import util.{fieldLabel, shorten}
import services.stats.calc.{GroupUniqueDistributionCountsCalcIOTypes, UniqueDistributionCountsCalcIOTypes}
import services.widgetgen._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[WidgetGenerationServiceImpl])
trait WidgetGenerationService {

  def apply(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]] = Nil,
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]] = Map(),
    fields: Traversable[Field],
    usePerWidgetRepoMethod: Boolean = false
  ): Future[Traversable[Option[(Widget, Seq[String])]]]

  def genFromRepo(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Future[Option[Widget]]

  def genLocally(
    widgetSpec: WidgetSpec,
    items: Traversable[JsObject],
    fields: Traversable[Field]
  ): Option[Widget]
}

@Singleton
class WidgetGenerationServiceImpl @Inject() (
    statsService: StatsService
  ) extends WidgetGenerationService {

  private implicit val ftf = FieldTypeHelper.fieldTypeFactory()
  private val maxIntCountsForZeroPadding = 1000

  override def apply(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    fields: Traversable[Field],
    usePerWidgetRepoMethod: Boolean
  ): Future[Traversable[Option[(Widget, Seq[String])]]] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap

    // a helper function to get the sub criteria for the widget
    def getSubCriteria(filterId: Option[BSONObjectID]): Seq[Criterion[Any]] =
      filterId.map(subFilterId =>
        widgetFilterSubCriteriaMap.get(subFilterId).getOrElse(Nil)
      ).getOrElse(Nil)

    val splitWidgetSpecs: Traversable[Either[WidgetSpec, WidgetSpec]] =
      if (usePerWidgetRepoMethod)
        widgetSpecs.collect {
          case p: DistributionWidgetSpec => Left(p)
          case p: BoxWidgetSpec => Left(p)
          case p: CumulativeCountWidgetSpec => if (p.numericBinCount.isDefined) Left(p) else Right(p)
          case p: ScatterWidgetSpec => Right(p)
          case p: CorrelationWidgetSpec => Right(p)
          case p: BasicStatsWidgetSpec => Right(p)
          case p: TemplateHtmlWidgetSpec => Left(p)
        }
      else
        widgetSpecs.map(Right(_))

    val repoWidgetSpecs = splitWidgetSpecs.map(_.left.toOption).flatten
    val fullDataWidgetSpecs = splitWidgetSpecs.map(_.right.toOption).flatten
    val fullDataFieldNames = fullDataWidgetSpecs.map(_.fieldNames).flatten.toSet

    println("Loaded chart field names: " + fullDataFieldNames.mkString(", "))

    val repoWidgetSpecsFuture =
      Future.sequence(
        repoWidgetSpecs.par.map { widgetSpec =>

          val newCriteria = criteria ++ getSubCriteria(widgetSpec.subFilterId)
          genFromRepoAux(widgetSpec, repo, newCriteria, nameFieldMap).map { widget =>
            val widgetFieldNames = widget.map(widget => (widget, widgetSpec.fieldNames.toSeq))
            (widgetSpec, widgetFieldNames)
          }
        }.toList
      )

    val fullDataWidgetSpecsFuture =
      if (fullDataWidgetSpecs.nonEmpty) {
        Future.sequence(
          fullDataWidgetSpecs.groupBy(_.subFilterId).map { case (subFilterId, widgetSpecs) =>
            val fieldNames = widgetSpecs.map(_.fieldNames).flatten.toSet

            repo.find(criteria ++ getSubCriteria(subFilterId), Nil, fieldNames).map { chartData =>
              widgetSpecs.par.map { widgetSpec =>
                val widget = genLocally(widgetSpec, chartData, fields)
                val widgetFieldNames = widget.map(widget => (widget, widgetSpec.fieldNames.toSeq))
                (widgetSpec, widgetFieldNames)
              }.toList
            }
          }
        )
      } else
        Future(Nil)

    for {
      chartSpecs1 <- repoWidgetSpecsFuture
      chartSpecs2 <- fullDataWidgetSpecsFuture
    } yield {
      val specWidgetMap = (chartSpecs1 ++ chartSpecs2.flatten).toMap
      // return widgets in the specified order
      widgetSpecs.map(specWidgetMap.get).flatten
    }
  }

  override def genFromRepo(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Future[Option[Widget]] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    genFromRepoAux(widgetSpec, repo, criteria, nameFieldMap)
  }

  private def genFromRepoAux(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field]
  ): Future[Option[Widget]] = {

    // aux functions to retrieve a field by name
    def getField(name: String): Field =
      nameFieldMap.get(name).getOrElse(throw new AdaException(s"Field $name not found."))

    def getFieldO(name: Option[String]) = name.map(getField)

    widgetSpec match {

      case spec: DistributionWidgetSpec =>
        val field = getField(spec.fieldName)
        val groupField = getFieldO(spec.groupFieldName)
        calcDistributionCountsFromRepo(repo, criteria, field, groupField, spec.numericBinCount).map(
          DistributionWidgetGenerator(nameFieldMap, spec)
        )

      case spec: CumulativeCountWidgetSpec =>
        val field = getField(spec.fieldName)
        val groupField = getFieldO(spec.groupFieldName)
        calcCumulativeCountsFromRepo(repo, criteria, field, groupField, spec.numericBinCount).map(
          CumulativeCountWidgetGenerator(nameFieldMap, spec)
        )

      case spec: BoxWidgetSpec =>
        statsService.calcQuartilesFromRepo(repo, criteria, getField(spec.fieldName)).map(
          _.flatMap(BoxWidgetGenerator(nameFieldMap, spec))
        )

      case spec: CorrelationWidgetSpec =>
        val fields = spec.fieldNames.flatMap(nameFieldMap.get)
        statsService.calcPearsonCorrelationsStreamed(repo, criteria, fields).map(
          CorrelationWidgetGenerator(nameFieldMap, spec)
        )

      case spec: TemplateHtmlWidgetSpec =>
        val widget = HtmlWidget("", spec.content, spec.displayOptions)
        Future(Some(widget))

      case _ => Future(None)
    }
  }

  override def genLocally(
    widgetSpec: WidgetSpec,
    items: Traversable[JsObject],
    fields: Traversable[Field]
  ): Option[Widget] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    genLocallyAux(widgetSpec, items, nameFieldMap)
  }

  private def genLocallyAux(
    widgetSpec: WidgetSpec,
    items: Traversable[JsObject],
    nameFieldMap: Map[String, Field]
  ): Option[Widget] = {

    // aux functions to retrieve a field by name
    def getField(name: String): Field =
      nameFieldMap.get(name).getOrElse(throw new AdaException(s"Field $name not found."))

    def getFieldO(name: Option[String]) = name.map(getField)

    widgetSpec match {

      case spec: DistributionWidgetSpec =>
        val field = getField(spec.fieldName)
        val groupField = getFieldO(spec.groupFieldName)
        val countSeries = calcDistributionCounts(items, field, groupField, spec.numericBinCount)
        DistributionWidgetGenerator(nameFieldMap, spec)(countSeries)

      case spec: CumulativeCountWidgetSpec =>
        val field = getField(spec.fieldName)
        val groupField = getFieldO(spec.groupFieldName)
        val countSeries = calcCumulativeCounts(items, field, groupField, spec.numericBinCount)
        CumulativeCountWidgetGenerator(nameFieldMap, spec)(countSeries)

      case spec: BoxWidgetSpec =>
        val field = getField(spec.fieldName)
        statsService.calcQuartiles(items, field).flatMap(
          BoxWidgetGenerator(nameFieldMap, spec)
        )

      case spec: ScatterWidgetSpec =>
        val xField = getField(spec.xFieldName)
        val yField = getField(spec.yFieldName)
        val groupField = getFieldO(spec.groupFieldName)

        val data = calcScatterData(items, xField, yField, groupField)
        ScatterWidgetGenerator[Any, Any](nameFieldMap, spec)(data)

      case spec: CorrelationWidgetSpec =>
        val fields = widgetSpec.fieldNames.flatMap(nameFieldMap.get).toSeq
        val correlations = statsService.calcPearsonCorrelations(items, fields)
        CorrelationWidgetGenerator(nameFieldMap, spec)(correlations)

      case spec: BasicStatsWidgetSpec =>
        val field = getField(spec.fieldName)
        statsService.calcBasicStats(items, field).flatMap (
          BasicStatsWidgetGenerator(nameFieldMap, spec)
        )

      case spec: TemplateHtmlWidgetSpec =>
        val widget = HtmlWidget("", spec.content, spec.displayOptions)
        Some(widget)
    }
  }

  private def calcDistributionCountsFromRepo(
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Future[Traversable[(String, Traversable[Count[Any]])]] =
    groupField match {
      case Some(groupField) =>
        field.fieldType match {

          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
            statsService.calcGroupedNumericDistributionCountsFromRepo(repo, criteria, field, groupField, numericBinCount).map { groupCounts =>
              val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
              createGroupNumericCounts(groupCounts, groupFieldType, field)
            }

          case _ =>
            statsService.calcGroupedUniqueDistributionCountsFromRepo(repo, criteria, field, groupField).map { groupCounts =>
              val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
              val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

              createGroupStringCounts(groupCounts, groupFieldType, fieldType)
            }
        }

      case None =>
        field.fieldType match {
          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
            statsService.calcNumericDistributionCountsFromRepo(repo, criteria, field, numericBinCount).map { numericCounts =>
              val counts = createNumericCounts(numericCounts, convertNumeric(field.fieldType))
              Seq(("All", counts))
            }

          case _ =>
            statsService.calcUniqueDistributionCountsFromRepo(repo, criteria, field).map { counts =>
              val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
              Seq(("All", createStringCounts(counts, fieldType)))
            }
        }
    }

  private def convertNumeric(fieldType: FieldTypeId.Value) =
    fieldType match {
      case FieldTypeId.Date =>
        val convert = {ms: BigDecimal => new java.util.Date(ms.setScale(0, BigDecimal.RoundingMode.CEILING).toLongExact)}
        Some(convert)
      case _ => None
    }

  private def calcDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Traversable[(String, Traversable[Count[Any]])] =
    groupField match {
      case Some(groupField) =>
        field.fieldType match {
          // group numeric
          case FieldTypeId.Double | FieldTypeId.Date =>
            countGroupNumericDistributionCounts(items, field, groupField, numericBinCount)

          // group numeric or unique depending on whether bin counts are defined
          case FieldTypeId.Integer =>
            numericBinCount match {

              case Some(_) =>
                countGroupNumericDistributionCounts(items, field, groupField, numericBinCount)

              case None =>
                countGroupUniqueIntDistributionCounts(items, field,  groupField)
            }

          // group unique
          case _ => countGroupUniqueDistributionCounts(items, field, groupField)
        }

      case None =>
        val counts = field.fieldType match {
          // numeric
          case FieldTypeId.Double | FieldTypeId.Date =>
            countNumericDistributionCounts(items, field, numericBinCount)

          // numeric or unique depending on whether bin counts are defined
          case FieldTypeId.Integer =>
            numericBinCount match {

              case Some(_) =>
                countNumericDistributionCounts(items, field, numericBinCount)

              case None =>
                countUniqueIntDistributionCounts(items, field)
            }

          // unique
          case _ => countUniqueDistributionCounts(items, field)
        }

        Seq(("All", counts))
    }

  private def countGroupNumericDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field,
    numericBinCount: Option[Int]
  ) = {
    val groupCounts = statsService.calcGroupedNumericDistributionCounts(items, field, groupField, numericBinCount)
    val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
    createGroupNumericCounts(groupCounts, groupFieldType, field)
  }

  private def countGroupUniqueDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field
  ) = {
    val groupCounts = statsService.calcGroupedUniqueDistributionCounts(items, field, groupField)
    val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
    val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

    createGroupStringCounts(groupCounts, groupFieldType, fieldType)
  }

  private def countGroupUniqueIntDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field
  ) = {
    val groupCounts = statsService.calcGroupedUniqueDistributionCounts(items, field, groupField)
    val longGroupCounts = groupCounts.asInstanceOf[GroupUniqueDistributionCountsCalcIOTypes.OUT[Any, Long]]
    val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

    toGroupStringValues(longGroupCounts, groupFieldType).map {
      case (groupString, valueCounts) =>
        (groupString, prepareIntCounts(valueCounts))
    }
  }

  private def countNumericDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    numericBinCount: Option[Int]
  ) = {
    val counts = statsService.calcNumericDistributionCounts(items, field, numericBinCount)
    createNumericCounts(counts, convertNumeric(field.fieldType))
  }

  private def countUniqueDistributionCounts(
    items: Traversable[JsObject],
    field: Field
  ) = {
    val uniqueCounts = statsService.calcUniqueDistributionCounts(items, field)
    val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
    createStringCounts(uniqueCounts, fieldType)
  }

  private def countUniqueIntDistributionCounts(
    items: Traversable[JsObject],
    field: Field
  ) = {
    val uniqueCounts = statsService.calcUniqueDistributionCounts(items, field)
    val longUniqueCounts = uniqueCounts.asInstanceOf[UniqueDistributionCountsCalcIOTypes.OUT[Long]]

    prepareIntCounts(longUniqueCounts)
  }

  private def prepareIntCounts(
    longUniqueCounts: UniqueDistributionCountsCalcIOTypes.OUT[Long]
  ) = {
    val counts = longUniqueCounts.collect{ case (Some(value), count) => Count(value, count)}.toSeq.sortBy(_.value)
    val size = counts.size

    val min = counts.head.value
    val max = counts.last.value

    // if the difference between max and min is "small" enough we can add a zero count for missing values
    if (Math.abs(max - min) < maxIntCountsForZeroPadding) {
      val countsWithZeroes = for (i <- 0 to size - 1) yield {
        val count = counts(i)
        if (i + 1 < size) {
          val nextCount = counts(i + 1)

          val zeroCounts =
            for (missingValue <- count.value + 1 to nextCount.value - 1) yield Count(missingValue, 0)

          Seq(count) ++ zeroCounts
        } else
          Seq(count)
      }

      countsWithZeroes.flatten
    } else
      counts
  }

  private def createStringCounts[T](
    counts: Traversable[(Option[T], Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map { case (value, count) =>
      val stringKey = value.map(_.toString)
      val label = value.map(value => fieldType.valueToDisplayString(Some(value))).getOrElse("Undefined")
      Count(label, count, stringKey)
    }

  private def createStringCountsDefined[T](
    counts: Traversable[(T, Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map { case (value, count) =>
      val stringKey = value.toString
      val label = fieldType.valueToDisplayString(Some(value))
      Count(label, count, Some(stringKey))
    }

  private def createNumericCounts(
    counts: Traversable[(BigDecimal, Int)],
    convert: Option[BigDecimal => Any] = None
  ): Seq[Count[_]] =
    counts.toSeq.sortBy(_._1).map { case (xValue, count) =>
      val convertedValue = convert.map(_.apply(xValue)).getOrElse(xValue.toDouble)
      Count(convertedValue, count, None)
    }

  private def createGroupStringCounts[G, T](
    groupCounts: Traversable[(Option[G], Traversable[(Option[T], Int)])],
    groupFieldType: FieldType[G],
    fieldType: FieldType[T]
  ): Seq[(String, Traversable[Count[String]])] =
    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, counts) =>
      (groupString, createStringCounts(counts, fieldType))
    }

  private def createGroupNumericCounts[G](
    groupCounts: Traversable[(Option[G], Traversable[(BigDecimal, Int)])],
    groupFieldType: FieldType[G],
    field: Field
  ): Seq[(String, Traversable[Count[_]])] = {
    // value converter
    val convert = convertNumeric(field.fieldType)

    // handle group string names and convert values
    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, counts) =>
      (groupString, createNumericCounts(counts, convert))
    }
  }

  private def toGroupStringValues[G, T](
    groupCounts: Traversable[(Option[G], Traversable[T])],
    groupFieldType: FieldType[G]
  ): Seq[(String, Traversable[T])] =
    groupCounts.toSeq.sortBy(_._1.isEmpty).map { case (group, values) =>
      val groupString = group match {
        case Some(group) => groupFieldType.valueToDisplayString(Some(group))
        case None => "Undefined"
      }
      (groupString, values)
    }

  private def calcCumulativeCountsFromRepo(
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Future[Traversable[(String, Traversable[Count[Any]])]] =
    numericBinCount match {
      case Some(_) =>
        calcDistributionCountsFromRepo(repo, criteria, field, groupField, numericBinCount).map( distCountsSeries =>
          toCumCounts(distCountsSeries.toSeq)
        )

      case None =>
        val projection = Seq(field.name) ++ groupField.map(_.name)

        repo.find(criteria, Nil, projection.toSet).map( jsons =>
          calcCumulativeCounts(jsons, field, groupField, None)
        )
    }

  private def calcCumulativeCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Traversable[(String, Traversable[Count[Any]])] =
    numericBinCount match {
      case Some(_) =>
        val distCounts = calcDistributionCounts(items, field, groupField, numericBinCount)
        toCumCounts(distCounts)

      case None =>
        groupField match {
          case Some(groupField) =>
            field.fieldType match {
              // group numeric
              case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
                countGroupNumericCumulativeCounts(items, field, groupField)

              // group string
              case _ =>
                countGroupStringCumulativeCounts(items, field, groupField)
            }

          case None =>
            val counts = field.fieldType match {
              // numeric
              case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
                countNumericCumulativeCounts(items, field)

              // string
              case _ =>
                countStringCumulativeCounts(items, field)
            }

            Seq(("All", counts))
        }
    }

  private def countGroupNumericCumulativeCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field
  ) = {
    val groupCounts = statsService.calcGroupedCumulativeCounts(items, field, groupField)
    val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, valueCounts) =>
      val counts = valueCounts.map { case (value, count) => Count(value, count)}
      (groupString, counts)
    }
  }

  private def countGroupStringCumulativeCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field
  ) = {
    val groupCounts = statsService.calcGroupedCumulativeCounts(items, field, groupField)
    val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
    val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, valueCounts) =>
      (groupString, createStringCountsDefined(valueCounts, fieldType))
    }
  }

  private def countNumericCumulativeCounts(
    items: Traversable[JsObject],
    field: Field
  ) = {
    val valueCounts = statsService.calcCumulativeCounts(items, field)
    valueCounts.map { case (value, count) => Count(value, count)}
  }

  private def countStringCumulativeCounts(
    items: Traversable[JsObject],
    field: Field
  ) = {
    val valueCounts = statsService.calcCumulativeCounts(items, field)
    val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
    createStringCountsDefined(valueCounts, fieldType)
  }

  // function that converts dist counts to cumulative counts by applying simple running sum
  private def toCumCounts[T](
    distCountsSeries: Traversable[(String, Traversable[Count[T]])]
  ): Traversable[(String, Seq[Count[T]])] =
    distCountsSeries.map { case (seriesName, distCounts) =>
      val distCountsSeq = distCounts.toSeq
      val cumCounts = distCountsSeq.scanLeft(0) { case (sum, count) =>
        sum + count.count
      }
      val labeledDistCounts: Seq[Count[T]] = distCountsSeq.map(_.value).zip(cumCounts.tail).map { case (value, count) =>
        Count(value, count)
      }
      (seriesName, labeledDistCounts)
    }

  private def calcScatterData(
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field]
  ): Seq[(String, Traversable[(Any, Any)])] =
    groupField match {
      case Some(groupField) =>
        val dataAux = statsService.collectGroupedTuples(xyzItems, xField, yField, groupField)
        dataAux.map { case (groupName, values) => (groupName.getOrElse("Undefined"), values)}.toSeq

      case None =>
        val dataAux = statsService.collectTuples(xyzItems, xField, yField)
        Seq(("all", dataAux))
    }
}