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
import java.util

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

  def apply(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Future[Option[Widget]]

  def apply(
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
          applyAux(widgetSpec, repo, newCriteria, nameFieldMap).map { widget =>
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
                val widget = apply(widgetSpec, chartData, fields)
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

  override def apply(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Future[Option[Widget]] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    applyAux(widgetSpec, repo, criteria, nameFieldMap)
  }

  private def applyAux(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field]
  ): Future[Option[Widget]] = {
    val title = widgetSpec.displayOptions.title

    widgetSpec match {

      case DistributionWidgetSpec(fieldName, groupFieldName, subFilter, useRelativeValues, numericBinCount, useDateMonthBins, displayOptions) =>
        val field = nameFieldMap.get(fieldName)
        val groupField = groupFieldName.map(nameFieldMap.get).flatten

        field.map { field =>
          calcDistributionCounts(repo, criteria, field, groupField, numericBinCount).map { countSeries =>
            val chartTitle = title.getOrElse(getDistributionWidgetTitle(field, groupField))
            createDistributionWidget(countSeries, field, chartTitle, false, true, useRelativeValues, false, numericBinCount, displayOptions)
          }
        }.getOrElse(
          Future(None)
        )

      case CumulativeCountWidgetSpec(fieldName, groupFieldName, subFilter, useRelativeValues, numericBinCount, useDateMonthBins, displayOptions) =>
        val field = nameFieldMap.get(fieldName)
        val groupField = groupFieldName.map(nameFieldMap.get).flatten

        field.map { field =>
          calcCumulativeCounts(repo, criteria, field, groupField, numericBinCount).map { cumCountSeries =>
            val chartTitle = title.getOrElse(getCumulativeCountWidgetTitle(field, groupField))

            val nonZeroCountSeries = cumCountSeries.filter(_._2.exists(_.count > 0))
            val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
            if (nonZeroCountSeries.nonEmpty) {
              val widget = NumericalCountWidget(chartTitle, field.labelOrElseName, useRelativeValues, true, nonZeroCountSeries, initializedDisplayOptions)
              Some(widget)
            } else
              None
          }
        }.getOrElse(
          Future(None)
        )

      case BoxWidgetSpec(fieldName, subFilter, displayOptions) =>
        val field = nameFieldMap.get(fieldName).get
        for {
          quantiles <- statsService.calcQuartiles(repo, criteria, field)
        } yield
          quantiles.map { quants =>
            implicit val ordering = quants.ordering
            val chartTitle = title.getOrElse(field.labelOrElseName)
            BoxWidget(chartTitle, field.labelOrElseName, quants, None, None, displayOptions)
          }

      case CorrelationWidgetSpec(fieldNames, subFilter, displayOptions) =>
        val fields = fieldNames.map(nameFieldMap.get).flatten

        for {
          correlations <- statsService.calcPearsonCorrelationsStreamed(repo, criteria, fields)
        } yield
          if (correlations.nonEmpty) {
            val chartTitle = title.getOrElse("Correlations")
            val widget = createCorrelationWidget(Some(chartTitle), correlations, fields, displayOptions)
            Some(widget)
          } else
            None

      case TemplateHtmlWidgetSpec(content, subFilter, displayOptions) =>
        val widget = HtmlWidget("", content, displayOptions)
        Future(Some(widget))

      case _ => Future(None)
    }
  }

  override def apply(
    widgetSpec: WidgetSpec,
    items: Traversable[JsObject],
    fields: Traversable[Field]
  ): Option[Widget] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    applyAux(widgetSpec, items, nameFieldMap)
  }

  private def applyAux(
    widgetSpec: WidgetSpec,
    items: Traversable[JsObject],
    nameFieldMap: Map[String, Field]
  ): Option[Widget] = {
    val title = widgetSpec.displayOptions.title

    widgetSpec match {

      case DistributionWidgetSpec(fieldName, groupFieldName, subFilter, useRelativeValues, numericBinCount, useDateMonthBins, displayOptions) =>
        val field = nameFieldMap.get(fieldName)
        val groupField = groupFieldName.map(nameFieldMap.get).flatten

        field.map { field =>
          val countSeries = calcDistributionCounts(items, field, groupField, numericBinCount)
          val chartTitle = title.getOrElse(getDistributionWidgetTitle(field, groupField))
          createDistributionWidget(countSeries, field, chartTitle, false, true, useRelativeValues, false, numericBinCount, displayOptions)
        }.flatten

      case CumulativeCountWidgetSpec(fieldName, groupFieldName, subFilter, useRelativeValues, numericBinCount, useDateMonthBins, displayOptions) =>
        val field = nameFieldMap.get(fieldName).get
        val groupField = groupFieldName.map(nameFieldMap.get).flatten

        val series = calcCumulativeCounts(items, field, groupField, numericBinCount)
        val nonZeroCountSeries = series.filter(_._2.exists(_.count > 0))

        val chartTitle = title.getOrElse(getCumulativeCountWidgetTitle(field, groupField))

        val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
        val chartSpec = NumericalCountWidget(chartTitle, field.labelOrElseName, useRelativeValues, true, nonZeroCountSeries, initializedDisplayOptions)
        Some(chartSpec)

      case BoxWidgetSpec(fieldName, subFilter, displayOptions) =>
        nameFieldMap.get(fieldName).map { field =>
          statsService.calcQuartiles(items, field).map { quants =>
            implicit val ordering = quants.ordering
            val chartTitle = title.getOrElse(field.labelOrElseName)
            BoxWidget(chartTitle, field.labelOrElseName, quants, None, None, displayOptions)
          }
        }.flatten

      case ScatterWidgetSpec(xFieldName, yFieldName, groupFieldName, subFilter, displayOptions) =>
        val xField = nameFieldMap.get(xFieldName).get
        val yField = nameFieldMap.get(yFieldName).get
        val shortXFieldLabel = shorten(xField.labelOrElseName, 20)
        val shortYFieldLabel = shorten(yField.labelOrElseName, 20)

        val chartTitle = title.getOrElse(s"$shortXFieldLabel vs. $shortYFieldLabel")

        if (items.nonEmpty) {
          val widget = createScatter(
            Some(chartTitle),
            items,
            xField,
            yField,
            groupFieldName.map(nameFieldMap.get).flatten,
            displayOptions
          )
          Some(widget)
        } else
          None

      case CorrelationWidgetSpec(fieldNames, subFilter, displayOptions) =>
        val corrFields = fieldNames.map(nameFieldMap.get).flatten
        val chartTitle = title.getOrElse("Correlations")
        val correlations = statsService.calcPearsonCorrelations(items, corrFields)

        if (items.nonEmpty) {
          val widget = createCorrelationWidget(Some(chartTitle), correlations, corrFields, displayOptions)
          Some(widget)
        } else
          None

      case BasicStatsWidgetSpec(fieldName, subFilter, displayOptions) =>
        nameFieldMap.get(fieldName).flatMap { field =>
          statsService.calcBasicStats(items, field).map { basicStats =>
            val chartTitle = title.getOrElse(field.labelOrElseName)
            BasicStatsWidget(chartTitle, field.labelOrElseName, basicStats, displayOptions)
          }
        }

      case TemplateHtmlWidgetSpec(content, subFilter, displayOptions) =>
        val widget = HtmlWidget("", content, displayOptions)
        Some(widget)
    }
  }

  private def createDistributionWidget(
    countSeries: Traversable[(String, Traversable[Count[Any]])],
    field: Field,
    chartTitle: String,
    showLabels: Boolean,
    showLegend: Boolean,
    useRelativeValues: Boolean,
    isCumulative: Boolean,
    maxCategoricalBinCount: Option[Int],
    displayOptions: MultiChartDisplayOptions
  ): Option[Widget] = {
    val fieldTypeId = field.fieldTypeSpec.fieldType

    val nonZeroCountExists = countSeries.exists(_._2.exists(_.count > 0))
    if (nonZeroCountExists)
      Some(
        fieldTypeId match {
          case FieldTypeId.String | FieldTypeId.Enum | FieldTypeId.Boolean | FieldTypeId.Null | FieldTypeId.Json => {
            // enforce the same categories in all the series
            val labelGroupedCounts = countSeries.map(_._2).flatten.groupBy(_.value)
            val nonZeroLabelSumCounts = labelGroupedCounts.map { case (label, counts) =>
              (label, counts.map(_.count).sum)
            }.filter(_._2 > 0)

            val sortedLabels: Seq[String] = nonZeroLabelSumCounts.toSeq.sortBy(_._2).map(_._1.toString)
            val topSortedLabels  = maxCategoricalBinCount match {
              case Some(maxCategoricalBinCount) => sortedLabels.takeRight(maxCategoricalBinCount)
              case None => sortedLabels
            }

            val countSeriesSorted = countSeries.map { case (seriesName, counts) =>

              val labelCountMap = counts.map { count =>
                val label = count.value.toString
                (label, Count(label, count.count, count.key))
              }.toMap

              val newCounts = topSortedLabels.map ( label =>
                labelCountMap.get(label).getOrElse(Count(label, 0, None))
              )
              (seriesName, newCounts)
            }
            val nonZeroCountSeriesSorted = countSeriesSorted.filter(_._2.exists(_.count > 0)).toSeq

            val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Pie)))
            CategoricalCountWidget(chartTitle, field.name, field.labelOrElseName, showLabels, showLegend, useRelativeValues, isCumulative, nonZeroCountSeriesSorted, initializedDisplayOptions)
          }

          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date => {
            val nonZeroNumCountSeries = countSeries.filter(_._2.nonEmpty).toSeq
            val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Line)))
            NumericalCountWidget(chartTitle, field.labelOrElseName, useRelativeValues, isCumulative, nonZeroNumCountSeries, initializedDisplayOptions)
          }
        }
      )
    else
      Option.empty[Widget]
  }

  private def calcDistributionCounts(
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
            statsService.calcGroupedNumericDistributionCounts(repo, criteria, field, groupField, numericBinCount).map { groupCounts =>
              val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
              createGroupNumericCounts(groupCounts, groupFieldType, convertNumeric(field.fieldType))
            }

          case _ =>
            statsService.calcGroupedUniqueDistributionCounts(repo, criteria, field, groupField).map { groupCounts =>
              val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
              val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

              createGroupStringCounts(groupCounts, groupFieldType, fieldType)
            }
        }

      case None =>
        field.fieldType match {
          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
            statsService.calcNumericDistributionCounts(repo, criteria, field, numericBinCount).map { numericCounts =>
              val counts = createNumericCounts(numericCounts, convertNumeric(field.fieldType))
              Seq(("All", counts))
            }

          case _ =>
            statsService.calcUniqueDistributionCounts(repo, criteria, field).map { counts =>
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
          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
            val groupCounts = statsService.calcGroupedNumericDistributionCounts(items, field, groupField, numericBinCount)
            val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
            createGroupNumericCounts(groupCounts, groupFieldType, convertNumeric(field.fieldType))

          case _ =>
            val groupCounts = statsService.calcGroupedUniqueDistributionCounts(items, field, groupField)
            val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
            val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

            createGroupStringCounts(groupCounts, groupFieldType, fieldType)
        }

      case None =>
        val counts = field.fieldType match {
          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
            val counts = statsService.calcNumericDistributionCounts(items, field, numericBinCount)
              createNumericCounts(counts, convertNumeric(field.fieldType))

          case _ =>
            val uniqueCounts = statsService.calcUniqueDistributionCounts(items, field)
            val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
            createStringCounts(uniqueCounts, fieldType)
        }

        Seq(("All", counts))
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
  ): Seq[(String, Traversable[Count[String]])] = {
    groupCounts.toSeq.sortBy(_._1.isEmpty).map { case (group, counts) =>
      val groupString = group match {
        case Some(group) => groupFieldType.valueToDisplayString(Some(group))
        case None => "Undefined"
      }
      val stringCounts = createStringCounts(counts, fieldType)
      (groupString, stringCounts)
    }
  }

  private def createGroupNumericCounts[G, T](
    groupCounts: Traversable[(Option[G], Traversable[(BigDecimal, Int)])],
    groupFieldType: FieldType[G],
    convert: Option[BigDecimal => Any] = None
  ): Seq[(String, Traversable[Count[_]])] =
    groupCounts.toSeq.sortBy(_._1.isEmpty).map { case (group, counts) =>
      val groupString = group match {
        case Some(group) => groupFieldType.valueToDisplayString(Some(group))
        case None => "Undefined"
      }
      val numericCounts = createNumericCounts(counts, convert)
      (groupString, numericCounts)
    }

  private def calcCumulativeCounts(
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Future[Seq[(String, Traversable[Count[Any]])]] =
    numericBinCount match {
      case Some(_) =>
        calcDistributionCounts(repo, criteria, field, groupField, numericBinCount).map( distCountsSeries =>
          toCumCounts(distCountsSeries.toSeq)
        )

      case None =>
        val projection = Seq(field.name) ++ groupField.map(_.name)

        repo.find(criteria, Nil, projection.toSet).map( jsons =>
          statsService.calcCumulativeCounts(jsons, field, groupField)
        )
    }

  private def calcCumulativeCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Seq[(String, Traversable[Count[Any]])] =
    numericBinCount match {
      case Some(_) =>
        val distCountsSeries = calcDistributionCounts(items, field, groupField, numericBinCount)
        toCumCounts(distCountsSeries.toSeq)

      case None =>
        statsService.calcCumulativeCounts(items, field, groupField)
    }

  // function that converts dist counts to cumulative counts by applying simple running sum
  private def toCumCounts[T](
    distCountsSeries: Seq[(String, Traversable[Count[T]])]
  ): Seq[(String, Seq[Count[T]])] =
    distCountsSeries.map { case (seriesName, distCounts) =>
      val distCountsSeq = distCounts.toSeq
      val cumCounts = distCountsSeq.scanLeft(0) { case (sum, count) =>
        sum + count.count
      }
      val labeledDistCounts: Seq[Count[T]] = distCountsSeq.map(_.value).zip(cumCounts).map { case (value, count) =>
        Count(value, count, None)
      }
      (seriesName, labeledDistCounts)
    }

  private def getDistributionWidgetTitle(
    field: Field,
    groupField: Option[Field]
  ): String = {
    val label = field.label.getOrElse(fieldLabel(field.name))
    groupField.map { groupField =>
      val groupShortLabel = shorten(groupField.label.getOrElse(fieldLabel(groupField.name)), 25)
      shorten(label, 25) + " by " + groupShortLabel
    }.getOrElse(
      label
    )
  }

  private def getCumulativeCountWidgetTitle(
    field: Field,
    groupField: Option[Field]
  ): String = {
    val label = field.label.getOrElse(fieldLabel(field.name))
    groupField match {
      case Some(groupField) =>
        val groupShortLabel = shorten(groupField.label.getOrElse(fieldLabel(groupField.name)), 25)
        shorten(label, 25) + " by " + groupShortLabel
      case None => label
    }
  }

  private def createScatter(
    title: Option[String],
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field],
    displayOptions: DisplayOptions
  ): ScatterWidget[_, _] = {
    val data = statsService.collectScatterData(xyzItems, xField, yField, groupField)

    ScatterWidget(
      title.getOrElse("Comparison"),
      xField.labelOrElseName,
      yField.labelOrElseName,
      data.toSeq.sortBy(_._1),
      displayOptions
    )
  }

  private def createCorrelationWidget(
    title: Option[String],
    correlations: Seq[Seq[Option[Double]]],
    fields: Seq[Field],
    displayOptions: DisplayOptions
  ): HeatmapWidget = {
    val fieldLabels = fields.map(_.labelOrElseName)
    HeatmapWidget(
      title.getOrElse("Correlations"),
      fieldLabels,
      fieldLabels,
      correlations,
      Some(-1),
      Some(1),
      displayOptions
    )
  }
}