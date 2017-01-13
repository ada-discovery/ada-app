package services

import java.text.SimpleDateFormat

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes.JsonCrudRepo
import dataaccess._
import models._
import play.api.libs.iteratee.Input.Empty
import play.api.libs.json.{JsLookupResult, JsObject}
import reactivemongo.bson.BSONObjectID
import util.BasicStats.Quantiles
import scala.collection.mutable.{Map => MMap}
import util._
import util.JsonUtil.project
import java.{util => ju}
import Criterion.Infix
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode
import scala.concurrent.Await.result
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[ChartServiceImpl])
trait ChartService {

  def createDistributionChartSpec(
    items: Traversable[JsObject],
    chartType: Option[ChartType.Value],
    field: Field,
    showLabels: Boolean,
    showLegend: Boolean,
    outputGridWidth: Option[Int] = None
  ): Option[ChartSpec]

  def createDistributionChartSpec(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    chartType: Option[ChartType.Value],
    field: Field,
    showLabels: Boolean,
    showLegend: Boolean,
    outputGridWidth: Option[Int]
  ): Future[Option[ChartSpec]]

  def createBoxChartSpec(
    items: Traversable[JsObject],
    field: Field,
    outputGridWidth: Option[Int] = None
  ): Option[BoxChartSpec[_]]

  def createBoxChartSpecRepo(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    outputGridWidth: Option[Int] = None
  ): Future[Option[BoxChartSpec[_]]]

  def createBoxChartSpecRepoNumeric[T](
    toDouble: T => Double,
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    outputGridWidth: Option[Int] = None
  ): Future[Option[BoxChartSpec[T]]]

  def createScatterChartSpec(
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field],
    title: Option[String] = None,
    outputGridWidth: Option[Int] = None
  ): ScatterChartSpec

  def createPearsonCorrelationChartSpec(
    items: Traversable[JsObject],
    fields: Traversable[Field],
    outputGridWidth: Option[Int] = None
  ): HeatmapChartSpec
}

class ChartServiceImpl extends ChartService {

  private val ftf = FieldTypeHelper.fieldTypeFactory
  protected val timeout = 200000 millis

  override def createDistributionChartSpec(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    chartType: Option[ChartType.Value],
    field: Field,
    showLabels: Boolean,
    showLegend: Boolean,
    outputGridWidth: Option[Int]
  ): Future[Option[ChartSpec]] = {
    val chartTitle = field.label.getOrElse(fieldLabel(field.name))
    val fieldTypeSpec = field.fieldTypeSpec
    val fieldType = ftf(fieldTypeSpec)
    val fieldTypeId = fieldTypeSpec.fieldType

    def getRenderer[T]= {
      val typedFieldType = fieldType.asValueOf[T]

      { (value : Option[T]) =>
        value match {
          case Some(_) => typedFieldType.valueToDisplayString(value)
          case None => "Undefined"
        }
      }
    }

    def chartOrNone[T](
      values: Traversable[T],
      createChart: Traversable[T] => ChartSpec
    ): Option[ChartSpec] = values match {
      case Nil => None
      case values => Some(createChart(values))
    }

    fieldTypeId match {
      case FieldTypeId.String =>
        for {
          jsons <- dataRepo.find(criteria = criteria, projection = Seq(field.name))
        } yield {
          val typedFieldType = fieldType.asValueOf[String]
          val values = jsons.map(json => typedFieldType.jsonToValue(json \ field.name))

          chartOrNone(
            values,
            ChartSpec.categorical(
              _: Traversable[Option[String]], Some(getRenderer[String]), chartTitle, showLabels, showLegend, chartType, outputGridWidth
            )
          )
        }

      case FieldTypeId.Enum => {
        val createChart = ChartSpec.categoricalPlain(
           _ : Seq[(Option[Int], Int)], Some(getRenderer[Int]), chartTitle, showLabels, showLegend, chartType, outputGridWidth
        )
        val values = fieldTypeSpec.enumValues.map(_.map(_._1)).getOrElse(Nil)
        createDistributionChartSpec(field.name, values, dataRepo, criteria, createChart)
      }

      case FieldTypeId.Boolean => {
        val createChart = ChartSpec.categoricalPlain(
           _ : Seq[(Option[Boolean], Int)], Some(getRenderer[Boolean]), chartTitle, showLabels, showLegend, chartType, outputGridWidth
        )
        val values = Seq(true, false)
        createDistributionChartSpec(field.name, values, dataRepo, criteria, createChart)
      }

      case FieldTypeId.Double => {
        // TODO: use renderer here
        val renderer = getRenderer[Double]
        def outputLabel(value: BigDecimal) = value.setScale(1, RoundingMode.HALF_UP).toString

        for {
          numCounts <-
            numericalCountsRepo(
              BigDecimal(_: Double), _.toDouble,
              field.name, fieldType.asValueOf[Double], dataRepo, criteria, 20, false, None, None
            )
        } yield
          chartOrNone(
            numCounts,
            ChartSpec.numerical(
              _: Traversable[(BigDecimal, Int)], chartTitle, chartType, Some(outputLabel), outputGridWidth
            )
          )
      }

      case FieldTypeId.Integer =>
        for {
          numCounts <-
            numericalCountsRepo(
              BigDecimal(_: Long), _.toDouble,
              field.name, fieldType.asValueOf[Long], dataRepo, criteria, 20, true, None, None
            )
        } yield {
          val outputLabel =
            if (numCounts.length < 20)
              Some { value: BigDecimal => value.toInt.toString }
            else
              None

          chartOrNone(
            numCounts,
            ChartSpec.numerical(
              _: Traversable[(BigDecimal, Int)], chartTitle, chartType, outputLabel, outputGridWidth)
            )
        }

      case FieldTypeId.Date => {
        val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
        def formatDate(ms: BigDecimal) = dateFormat.format(new ju.Date(ms.toLongExact))

        for {
          numCounts <-
            numericalCountsRepo(
              {x : ju.Date => BigDecimal(x.getTime)},
              {x : BigDecimal => new ju.Date(x.toLongExact)},
              field.name, fieldType.asValueOf[ju.Date], dataRepo, criteria, 20, false, None, None
            )
        } yield
          chartOrNone(
            numCounts,
            ChartSpec.numerical(
              _: Traversable[(BigDecimal, Int)], chartTitle, chartType, Some(formatDate), outputGridWidth
            )
          )
      }

      // for null and json types we can't show anything
      case FieldTypeId.Null | FieldTypeId.Json =>
        Future(None)
    }
  }

  override def createDistributionChartSpec(
    items: Traversable[JsObject],
    chartType: Option[ChartType.Value],
    field: Field,
    showLabels: Boolean = false,
    showLegend: Boolean = true,
    outputGridWidth: Option[Int] = None
  ): Option[ChartSpec] = {
    val chartTitle = field.label.getOrElse(fieldLabel(field.name))
    val fieldTypeSpec = field.fieldTypeSpec
    val fieldType = ftf(fieldTypeSpec)
    val fieldTypeId = fieldTypeSpec.fieldType

    val jsons = project(items, field.name)

    def getValues[T]: Traversable[Option[T]] = {
      val typedFieldType = fieldType.asValueOf[T]
      jsons.map(typedFieldType.jsonToValue)
    }

    def getRenderer[T]= {
      val typedFieldType = fieldType.asValueOf[T]

      { (value : Option[T]) =>
        value match {
          case Some(_) => typedFieldType.valueToDisplayString(value)
          case None => "Undefined"
        }
      }
    }

    def chartOrNone[T](
      values: Traversable[T],
      createChart: Traversable[T] => ChartSpec
    ): Option[ChartSpec] = values match {
      case Nil => None
      case values => Some(createChart(values))
    }

    fieldTypeId match {

      case FieldTypeId.String =>
        chartOrNone(
          getValues[String],
          ChartSpec.categorical(
            _: Traversable[Option[String]], Some(getRenderer[String]), chartTitle, showLabels, showLegend, chartType, outputGridWidth
          )
        )

      case FieldTypeId.Enum =>
        chartOrNone(
          getValues[Int],
          ChartSpec.categorical(
            _: Traversable[Option[Int]], Some(getRenderer[Int]), chartTitle, showLabels, showLegend, chartType, outputGridWidth
          )
        )

      case FieldTypeId.Boolean =>
        chartOrNone(
          getValues[Boolean],
          ChartSpec.categorical(
            _: Traversable[Option[Boolean]], Some(getRenderer[Boolean]), chartTitle, showLabels, showLegend, chartType, outputGridWidth
          )
        )

      case FieldTypeId.Double => {
        // TODO: use renderer here
        val renderer = getRenderer[Double]
        def outputLabel(value: BigDecimal) = value.setScale(1, RoundingMode.HALF_UP).toString
        val numCounts = numericalCounts(getValues[Double].flatten, 20, false, None, None)

        chartOrNone(
          numCounts,
          ChartSpec.numerical(
            _: Traversable[(BigDecimal, Int)], chartTitle, chartType, Some(outputLabel), outputGridWidth
          )
        )
      }

      case FieldTypeId.Integer => {
        val values = getValues[Long].flatten
        val min = if (values.nonEmpty) values.min else 0
        val max = if (values.nonEmpty) values.max else 0
        val valueCount = max - min

        val outputLabel =
          if (valueCount < 20)
            Some { value: BigDecimal => value.toInt.toString }
          else
            None

        val numCounts = numericalCounts(values, Math.min(20, valueCount + 1).toInt, valueCount < 20, None, None)
        chartOrNone(
          numCounts,
          ChartSpec.numerical(
            _: Traversable[(BigDecimal, Int)], chartTitle, chartType, outputLabel, outputGridWidth
          )
        )
      }

      case FieldTypeId.Date => {
        val dates = getValues[ju.Date].flatten
        val values = dates.map(_.getTime)

        val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
        def formatDate(ms: BigDecimal) = dateFormat.format(new ju.Date(ms.toLongExact))

        val numCounts = numericalCounts(values, 20, false, None, None)

        chartOrNone(
          numCounts,
          ChartSpec.numerical(
            _: Traversable[(BigDecimal, Int)], chartTitle, chartType, Some(formatDate), outputGridWidth
          )
        )
      }

      // for null and json types we can't show anything
      case FieldTypeId.Null | FieldTypeId.Json =>
        None
    }
  }

  private def createDistributionChartSpec[T](
    fieldName: String,
    values: Traversable[T],
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    createChart: Seq[(Option[T], Int)] => ChartSpec
  ): Future[Option[ChartSpec]] = {
    val countsFuture = {
      val countFutures = values.par.map { value =>
        val finalCriteria = criteria ++ Seq(fieldName #== value)
          dataRepo.count(finalCriteria).map { count =>
          (Some(value), count)
        }
      }.toList

      val findNoneCriteria = criteria ++ Seq(fieldName #=@)
      val naValueFuture = dataRepo.count(findNoneCriteria).map { count =>
        (None, count)
      }

      Future.sequence(countFutures ++ Seq(naValueFuture))
    }

    countsFuture.map { counts =>
      val nonZeroCounts = counts.filter(_._2 > 0)
      nonZeroCounts match {
        case Nil => None
        case _ => Some(createChart(nonZeroCounts))
      }
    }
  }

  def numericalCounts[T: Numeric](
    values: Traversable[T],
    columnCount: Int,
    specialColumnForMax: Boolean = false,
    explMin: Option[T] = None,
    explMax: Option[T] = None
  ): Seq[(BigDecimal, Int)] = {
    val numeric = implicitly[Numeric[T]]

    if (values.nonEmpty) {

      val doubles = values.map(numeric.toDouble)

      val max = BigDecimal(
        if (explMax.isDefined)
          numeric.toDouble(explMax.get)
        else
          doubles.max
      )

      val min = BigDecimal(
        if (explMin.isDefined)
          numeric.toDouble(explMin.get)
        else
          doubles.min
      )

      val stepSize: BigDecimal =
        if (min == max)
          0
        else if (specialColumnForMax)
          (max - min) / (columnCount - 1)
        else
          (max - min) / columnCount

      val countMap = MMap[Int, Int]()

      // initialize counts to zero
      (0 until columnCount).foreach { index =>
        countMap.update(index, 0)
      }
      doubles.map { value =>
        val bucketIndex =
          if (stepSize.equals(BigDecimal(0)))
            0
          else if (value == max)
            columnCount - 1
          else
            ((value - min) / stepSize).setScale(0, RoundingMode.FLOOR).toInt

        val count = countMap.get(bucketIndex).get
        countMap.update(bucketIndex, count + 1)
      }

      countMap.toSeq.sortBy(_._1).map { case (index, count) =>
        val xValue = min + (index * stepSize)
        (xValue, count)
      }
    } else
      Seq[(BigDecimal, Int)]()
  }

  def numericalCountsRepo[T](
    toBigDecimal: T => BigDecimal,
    toRangeVal: BigDecimal => Any,
    fieldName: String,
    fieldType: FieldType[T],
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    maxColumnCount: Int,
    columnForEachIntValue: Boolean,
    explMin: Option[T],
    explMax: Option[T]
  ): Future[Seq[(BigDecimal, Int)]] = {
    def jsonToBigDecimalValue(json: JsObject): Option[BigDecimal] = {
      fieldType.jsonToValue(json \ fieldName).map(toBigDecimal)
    }

    for {
      maxOption <- if (explMax.isDefined)
        Future(Some(toBigDecimal(explMax.get)))
      else {
        val maxJsonFuture = dataRepo.find(
          criteria = criteria ++ Seq(fieldName #!@),
          projection = Seq(fieldName),
          sort = Seq(DescSort(fieldName)),
          limit = Some(1)
        ).map(_.headOption)

        maxJsonFuture.map(_.map(jsonToBigDecimalValue).flatten)
      }

      minOption <- if (explMin.isDefined)
        Future(Some(toBigDecimal(explMin.get)))
      else {
        val minJsonFuture = dataRepo.find(
          criteria = criteria ++ Seq(fieldName #!@),
          projection = Seq(fieldName),
          sort = Seq(AscSort(fieldName)),
          limit = Some(1)
        ).map(_.headOption)

        minJsonFuture.map(_.map(jsonToBigDecimalValue).flatten)
      }

      columnCountStepSizeOption: Option[(Int, BigDecimal)] =
        minOption.zip(maxOption).headOption.map { case (min, max) =>
          val columnCount =
            if (columnForEachIntValue) {
              val valueCount = max - min
              Math.min(maxColumnCount, valueCount.toInt + 1)
            } else
              maxColumnCount

          val stepSize: BigDecimal = if (min == max)
              0
            else if (columnForEachIntValue && columnCount < maxColumnCount)
              (max - min) / (columnCount - 1)
            else
              (max - min) / columnCount

          (columnCount, stepSize)
        }

      bucketCounts <-
        minOption.zip(columnCountStepSizeOption).headOption.map { case (min, (columnCount, stepSize)) =>
          if (stepSize == 0) {
            val rangeCriteria = Seq(fieldName #== toRangeVal(min))
            dataRepo.count(rangeCriteria ++ criteria).map(count =>
              Seq((min, count))
            )
          } else {
            val futures = (0 until columnCount).par.map { index =>

              val start = min + (index * stepSize)
              val end = min + ((index + 1) * stepSize)

              val startVal = toRangeVal(start)
              val endVal = toRangeVal(end)

              val rangeCriteria =
                if (index < columnCount - 1)
                  Seq(fieldName #>= startVal, fieldName #< endVal)
                else
                  Seq(fieldName #>= startVal, fieldName #<= endVal)

              dataRepo.count(rangeCriteria ++ criteria).map((start, _))
            }

            Future.sequence(futures.toList)
          }
        }.getOrElse(
          Future(Nil)
        )

    } yield
        bucketCounts
  }

  override def createScatterChartSpec(
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field],
    title: Option[String] = None,
    outputGridWidth: Option[Int] = None
  ): ScatterChartSpec = {
    val data = getScatterData(xyzItems, xField, yField, groupField)
    ScatterChartSpec(
      title.getOrElse("Comparison"),
      xField.labelOrElseName,
      yField.labelOrElseName,
      data.map { case (name, values) =>
        val initName = if (name.isEmpty) "Undefined" else name
        (initName, "rgba(223, 83, 83, .5)", values.map(pair => Seq(pair._1, pair._2)))
      },
      None,
      outputGridWidth
    )
  }

  private def getScatterData(
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field],
    outputGridWidth: Option[Int] = None
  ): Seq[(String, Seq[(Any, Any)])] = {
    val xFieldName = xField.name
    val yFieldName = yField.name

    val xFieldType = ftf(xField.fieldTypeSpec)
    val yFieldType = ftf(yField.fieldTypeSpec)

    val xyzSeq = xyzItems.toSeq
    val xJsons = project(xyzSeq, xFieldName).toSeq
    val yJsons = project(xyzSeq, yFieldName).toSeq

    val xValues = xJsons.map(xFieldType.jsonToValue)
    val yValues = yJsons.map(yFieldType.jsonToValue)

    groupField match {
      case Some(zField) => {
        val zFieldType = ftf(zField.fieldTypeSpec)
        val groupJsons = project(xyzSeq, zField.name).toSeq
        val groupValues = groupJsons.map(zFieldType.jsonToDisplayString)

        // TODO: simplify this
        (groupValues, xValues, yValues).zipped.map { case (zValue, xValue, yValue) =>
          (Some(zValue), xValue, yValue).zipped
        }.flatten.groupBy(_._1).map { case (zValue, values) =>
          (
            zValue,
            values.map(tupple => (tupple._2, tupple._3))
            )
        }.toSeq
      }
      case None => {
        val xys = (xValues, yValues).zipped.map { case (xValue, yValue) =>
          (xValue, yValue).zipped
        }.flatten

        Seq(("all", xys))
      }
    }
  }

  override def createBoxChartSpec(
    items: Traversable[JsObject],
    field: Field,
    outputGridWidth: Option[Int] = None
  ): Option[BoxChartSpec[_]] = {
    val jsons = project(items, field.name)
    val typeSpec = field.fieldTypeSpec
    val fieldType = ftf(typeSpec)

    def getValues[T]: Traversable[T] = {
      val typedFieldType = fieldType.asValueOf[T]
      jsons.map(typedFieldType.jsonToValue).flatten
    }

    def quantiles[T: Numeric]: Option[Quantiles[T]] =
      BasicStats.quantiles(getValues[T].toSeq)

    val quants = typeSpec.fieldType match {
      case FieldTypeId.Double => quantiles[Double]

      case FieldTypeId.Integer => quantiles[Long]

      case FieldTypeId.Date => {
        val values = getValues[ju.Date].map(_.getTime)
        BasicStats.quantiles(values.toSeq)
      }

      case _ => None
    }

    quants.map(quant => BoxChartSpec(field.labelOrElseName, field.labelOrElseName, quant, None, outputGridWidth))
  }

  override def createBoxChartSpecRepo(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    outputGridWidth: Option[Int] = None
  ): Future[Option[BoxChartSpec[_]]] = {
    val typeSpec = field.fieldTypeSpec

    def createChart[T](toDouble: T => Double) = createBoxChartSpecRepoNumeric[T](toDouble, dataRepo, criteria, field, outputGridWidth)

    typeSpec.fieldType match {
      case FieldTypeId.Double => createChart[Double](identity)

      case FieldTypeId.Integer => createChart[Long](_.toDouble)

      case FieldTypeId.Date => createChart[ju.Date](_.getTime.toDouble)

      case _ => Future(None)
    }
  }


  override def createBoxChartSpecRepoNumeric[T](
    toDouble: T => Double,
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    outputGridWidth: Option[Int] = None
  ): Future[Option[BoxChartSpec[T]]] = {
    for {
      // total length
      length <- dataRepo.count(criteria ++ Seq(field.name #!@))

      // create quantiles
      quants <-
        if (length > 0)
          createQuantiles(toDouble, length, dataRepo, criteria, field, outputGridWidth)
        else
          Future(None)
    } yield {
      quants.map {
        BoxChartSpec(field.labelOrElseName, field.labelOrElseName, _, None, outputGridWidth)
      }
    }
  }

  private def createQuantiles[T](
    toDouble: T => Double,
    length: Int,                                            // must be non-zero
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    outputGridWidth: Option[Int] = None
  ): Future[Option[Quantiles[T]]] = {
    val typeSpec = field.fieldTypeSpec
    val fieldType = ftf(typeSpec).asValueOf[T]

    def headResultToValue(
      results: Traversable[JsObject]
    ): Option[T] =
      results.headOption.map( json =>
        fieldType.jsonToValue(json \ field.name)
      ).flatten

    def getItem(position : Int, ascOrder: Boolean): Future[Option[T]] = {
//        fromValue.map { minValue => Seq(field.name #> minValue) }

      dataRepo.find(
        criteria = criteria ++ Seq(field.name #!@),
        projection = Seq(field.name),
        sort = Seq(if (ascOrder) AscSort(field.name) else DescSort(field.name)),
        skip = Some(Math.max(position - 1, 0)),
        limit = Some(1)
      ).map(headResultToValue)
    }

    for {
      // lower quartile
      lowerQuantileOption <- getItem(length / 4, true)

      // lower quartile less equals count
      lowerQuantileLessEqualsCountOption <-
        lowerQuantileOption match {
          case Some(lowerQuantile) =>
            dataRepo.count(
              criteria = criteria ++ Seq(field.name #<= lowerQuantile)
            ).map(Some(_))
          case None => Future(None)
        }

      //      // median
//      medianOption <- getItem(length / 2, true)

      // upper quartile
      upperQuantileOption <- getItem(length / 4, false)

      // lower and upper whiskers
      medianLowerUpperWhiskerOptions <- {
        val optionFuture = for {
          upper <- upperQuantileOption
          lower <- lowerQuantileOption
          lowerQuantileLessEqualsCount <- lowerQuantileLessEqualsCountOption
        } yield {
          val doubleUpper = toDouble(upper)
          val doubleLower = toDouble(lower)

          val iqr = doubleUpper - doubleLower

          val upperWhiskerValue = doubleUpper + 1.5 * iqr
          val lowerWhiskerValue = doubleLower - 1.5 * iqr

          val medianPos = length / 2
          val relativeMedianPos = medianPos - lowerQuantileLessEqualsCount

          val medianFuture =
            if (relativeMedianPos > 0) {
              dataRepo.find(
                criteria = criteria ++ Seq(field.name #> lower),
                projection = Seq(field.name),
                sort = Seq(AscSort(field.name)),
                skip = Some(relativeMedianPos - 1),
                limit = Some(1)
              ).map { results =>
                headResultToValue(results)
              }
            } else
              Future(Some(lower))

          val lowerWhiskerFuture =
            dataRepo.find(
              criteria = criteria ++ Seq(field.name #>= lowerWhiskerValue),
              projection = Seq(field.name),
              sort = Seq(AscSort(field.name)),
              limit = Some(1)
            ).flatMap { results =>
              headResultToValue(results) match {
                // if no value found take the first item
                case Some(value) => Future(Some(value))
                case None => getItem(1, true)
              }
            }

          val upperWhiskerFuture =
            dataRepo.find(
              criteria = criteria ++ Seq(field.name #<= upperWhiskerValue),
              projection = Seq(field.name),
              sort = Seq(DescSort(field.name)),
              limit = Some(1)
            ).flatMap { results =>
              headResultToValue(results) match {
                // if no value found take the last item
                case Some(value) => Future(Some(value))
                case None => getItem(1, false)
              }
            }

          for {
            median <- medianFuture
            lowerWhisker <- lowerWhiskerFuture
            upperWhisker <- upperWhiskerFuture
          } yield
            (median, lowerWhisker, upperWhisker)
        }
        optionFuture.getOrElse(
          Future(None, None, None)
        )
      }
    } yield {
      for {
        lowerWhisker <- medianLowerUpperWhiskerOptions._2
        lowerQuantile <- lowerQuantileOption
        median <- medianLowerUpperWhiskerOptions._1
        upperQuantile <- upperQuantileOption
        upperWhisker <- medianLowerUpperWhiskerOptions._3
      } yield {
        Quantiles(lowerWhisker, lowerQuantile, median, upperQuantile, upperWhisker)
      }
    }
  }

  override def createPearsonCorrelationChartSpec(
    items: Traversable[JsObject],
    fields: Traversable[Field],
    outputGridWidth: Option[Int] = None
  ): HeatmapChartSpec = {

    def getValues[T](field: Field): Traversable[Option[T]] = {
      val typedFieldType = ftf(field.fieldTypeSpec).asValueOf[T]
      project(items, field.name).map(typedFieldType.jsonToValue)
    }

    val fieldsWithValues: Traversable[(Field, Traversable[Option[Double]])] = fields.map { field =>
      field.fieldType match {
        case FieldTypeId.Double =>
          Some((field, getValues[Double](field)))

        case FieldTypeId.Integer =>
          Some((field, getValues[Long](field).map(_.map(_.toDouble))))

        case FieldTypeId.Date =>
          Some((field, getValues[java.util.Date](field).map(_.map(_.getTime.toDouble))))

        case _ => None
      }
    }.flatten

    val data: Seq[Seq[Option[Double]]] = fieldsWithValues.map(_._2).toSeq.transpose

//    val filteredData = data.filter(!_.contains(None)).map(_.flatten)
//
//    println("First")
//    println
//    println(filteredData.map(x => x(0)).mkString("\n"))
//
//    println
//    println("Second")
//    println
//    println(filteredData.map(x => x(1)).mkString("\n"))
//    println

    val correlations = BasicStats.pearsonCorrelation(data)

//    println("Correlations")
//    println(correlations.map(_.mkString(",")).mkString("\n"))

    val fieldLabels = fieldsWithValues.map(_._1.labelOrElseName).toSeq
    HeatmapChartSpec("Correlations", fieldLabels, fieldLabels, correlations, None, Some(-1), Some(1), outputGridWidth)
  }
}