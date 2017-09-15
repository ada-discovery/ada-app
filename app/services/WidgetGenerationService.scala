package services

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import dataaccess.Criterion
import dataaccess.RepoTypes.JsonReadonlyRepo
import models._
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import util.{fieldLabel, shorten}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

@ImplementedBy(classOf[WidgetGenerationServiceImpl])
trait WidgetGenerationService {

  def apply(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    fields: Traversable[Field],
    usePerWidgetRepoMethod: Boolean
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
          quantiles <- statsService.calcQuantiles(repo, criteria, field)
        } yield
          quantiles.map { quants =>
            implicit val ordering = quants.ordering
            val chartTitle = title.getOrElse(field.labelOrElseName)
            BoxWidget(chartTitle, field.labelOrElseName, quants, None, None, displayOptions)
          }

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
          statsService.calcQuantiles(items, field).map { quants =>
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

        if (items.nonEmpty) {
          val widget = createCorrelations(
            Some(chartTitle),
            items,
            corrFields,
            displayOptions
          )
          Some(widget)
        } else
          None

      case TemplateHtmlWidgetSpec(content, subFilter, displayOptions) =>
        val widget = HtmlWidget("", content, displayOptions)
        Some(widget)
    }
  }

  private def createDistributionWidget(
    countSeries: Seq[(String, Seq[Count[Any]])],
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
            val nonZeroCountSeriesSorted = countSeriesSorted.filter(_._2.exists(_.count > 0))

            val initializedDisplayOptions = displayOptions.copy(chartType = Some(displayOptions.chartType.getOrElse(ChartType.Pie)))
            CategoricalCountWidget(chartTitle, field.name, field.labelOrElseName, showLabels, showLegend, useRelativeValues, isCumulative, nonZeroCountSeriesSorted, initializedDisplayOptions)
          }

          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date => {
            val nonZeroNumCountSeries = countSeries.filter(_._2.nonEmpty)
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
  ): Future[Seq[(String, Seq[Count[Any]])]] =
    groupField match {
      case Some(groupField) =>
        statsService.calcGroupedDistributionCounts(repo, criteria, field, groupField, numericBinCount)
      case None =>
        statsService.calcDistributionCounts(repo, criteria, field, numericBinCount).map(counts =>
          Seq(("All", counts))
        )
    }

  private def calcDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Seq[(String, Seq[Count[Any]])] =
    groupField match {
      case Some(groupField) =>
        statsService.calcGroupedDistributionCounts(items, field, groupField, numericBinCount)

      case None =>
        val counts = statsService.calcDistributionCounts(items, field, numericBinCount)
        Seq(("All", counts))
    }

  private def calcCumulativeCounts(
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Future[Seq[(String, Seq[Count[Any]])]] =
    numericBinCount match {
      case Some(_) =>
        calcDistributionCounts(repo, criteria, field, groupField, numericBinCount).map( distCountsSeries =>
          toCumCounts(distCountsSeries)
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
  ): Seq[(String, Seq[Count[Any]])] =
    numericBinCount match {
      case Some(_) =>
        val distCountsSeries = calcDistributionCounts(items, field, groupField, numericBinCount)
        toCumCounts(distCountsSeries)

      case None =>
        statsService.calcCumulativeCounts(items, field, groupField)
    }

  // function that converts dist counts to cumulative counts by applying simple running sum
  private def toCumCounts[T](
    distCountsSeries: Seq[(String, Seq[Count[T]])]
  ): Seq[(String, Seq[Count[T]])] =
    distCountsSeries.map { case (seriesName, distCounts) =>
      val cumCounts = distCounts.scanLeft(0) { case (sum, count) =>
        sum + count.count
      }
      val labeledDistCounts: Seq[Count[T]] = distCounts.map(_.value).zip(cumCounts).map { case (value, count) =>
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
      data.map { case (name, values) =>
        val initName = if (name.isEmpty) "Undefined" else name
        (initName, "rgba(223, 83, 83, .5)", values)
      },
      displayOptions
    )
  }

  private def createCorrelations(
    title: Option[String],
    items: Traversable[JsObject],
    fields: Seq[Field],
    displayOptions: DisplayOptions
  ): HeatmapWidget = {
    val correlations = statsService.calcPearsonCorrelations(items, fields)

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