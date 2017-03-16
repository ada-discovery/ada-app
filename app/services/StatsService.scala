package services

import java.text.SimpleDateFormat

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes.JsonCrudRepo
import dataaccess._
import models._
import play.api.libs.iteratee.Input.Empty
import play.api.libs.json.{JsLookupResult, JsObject, JsValue}
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

@ImplementedBy(classOf[StatsServiceImpl])
trait StatsService {

  def calcDistributionCounts(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field
  ): Future[Seq[Count]]

  def calcDistributionCounts(
    items: Traversable[JsObject],
    field: Field
  ): Seq[Count]

  def calcGroupedDistributionCounts(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Field
  ): Future[Seq[(String, Seq[Count])]]

  def calcGroupedDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field
  ): Seq[(String, Seq[Count])]

  def categoricalCountsWithFormatting[T](
    values: Traversable[Option[T]],
    renderer: Option[Option[T] => String]
  ): Seq[Count]

  def createBoxChartSpec(
    items: Traversable[JsObject],
    field: Field,
    outputGridWidth: Option[Int] = None
  ): Option[BoxChartSpec[_]]

  def createBoxChartSpecRepo(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    outputGridWidth: Option[Int] = None,
    height: Option[Int] = None
  ): Future[Option[BoxChartSpec[_]]]

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

class StatsServiceImpl extends StatsService {

  private val ftf = FieldTypeHelper.fieldTypeFactory
  protected val timeout = 200000 millis

  override def calcDistributionCounts(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field
  ): Future[Seq[Count]] = {
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

    fieldTypeId match {
      case FieldTypeId.String =>
        for {
          jsons <- dataRepo.find(criteria = criteria, projection = Seq(field.name))
        } yield {
          val typedFieldType = fieldType.asValueOf[String]
          val values = jsons.map(json => typedFieldType.jsonToValue(json \ field.name))
          categoricalCountsWithFormatting(values, Some(getRenderer[String]))
        }

      case FieldTypeId.Enum => {
        val values = fieldTypeSpec.enumValues.map(_.map(_._1).toSeq.sorted).getOrElse(Nil)
        for {
          counts <- categoricalCountsRepo(field.name, values, dataRepo, criteria)
        } yield
          formatCategoricalCounts(counts, Some(getRenderer[Int]))
      }

      case FieldTypeId.Boolean => {
        val values = Seq(true, false)
        for {
          counts <- categoricalCountsRepo(field.name, values, dataRepo, criteria)
        } yield
          formatCategoricalCounts(counts, Some(getRenderer[Boolean]))
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
          formatNumericalCounts(numCounts, Some(outputLabel))
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

          formatNumericalCounts(numCounts, outputLabel)
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
          formatNumericalCounts(numCounts, Some(formatDate))
      }

      case FieldTypeId.Null =>
        for {
          count <- dataRepo.count(criteria)
        } yield
          formatCategoricalCounts[Nothing](Seq((None, count)), None)

      // for the json type we can't do anything
      case FieldTypeId.Json =>
        Future(Nil)
    }
  }

  def calcGroupedDistributionCounts(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Field
  ): Future[Seq[(String, Seq[Count])]] = {

    val groupFieldSpec = groupField.fieldTypeSpec
    val groupFieldType = ftf(groupFieldSpec)
    val groupFieldTypeId = groupFieldSpec.fieldType

    for {
      groupLabelValues <- groupFieldTypeId match {
        case FieldTypeId.String =>
          for {
            jsons <- dataRepo.find(criteria = criteria, projection = Seq(groupField.name))
          } yield {
            val typedFieldType = groupFieldType.asValueOf[String]
            val values = jsons.map(json => typedFieldType.jsonToValue(json \ groupField.name)).flatten
            values.toSet.toSeq.sorted.map(string => (string, string))
          }

        case FieldTypeId.Enum => {
          val values = groupFieldSpec.enumValues.map(_.toSeq.sortBy(_._1).map { case (int, label) =>
            (label, int)
          }).getOrElse(Nil)
          Future(values)
        }

        case FieldTypeId.Boolean =>
          val values = Seq(
            (groupFieldSpec.displayTrueValue.getOrElse("True"), true),
            (groupFieldSpec.displayFalseValue.getOrElse("False"), false)
          )
          Future(values)
      }

      seriesCounts <- {
        val groupFieldName = groupField.name
        val countFutures = groupLabelValues.par.map { case (label, value) =>
          val finalCriteria = criteria ++ Seq(groupFieldName #== value)
          calcDistributionCounts(dataRepo, finalCriteria, field).map { counts =>
            (label, counts)
          }
        }.toList

        val undefinedGroupCriteria = criteria ++ Seq(groupFieldName #=@)
        val naValueFuture = calcDistributionCounts(dataRepo, undefinedGroupCriteria, field).map { counts =>
          ("Undefined", counts)
        }

        Future.sequence(countFutures ++ Seq(naValueFuture))
      }
    } yield {
      val seriesCountsMap = seriesCounts.toMap
      val seriesNames = groupLabelValues.map(_._1) ++ Seq("Undefined")

      // series counts sorted by the order of group labels
      seriesNames.map { name =>
        val counts = seriesCountsMap.get(name).get
        (name, counts)
      }
    }
  }

  override def calcDistributionCounts(
    items: Traversable[JsObject],
    field: Field
  ): Seq[Count] = {
    val fieldTypeSpec = field.fieldTypeSpec
    val fieldType = ftf(fieldTypeSpec)
    val fieldTypeId = fieldTypeSpec.fieldType

    val jsons = project(items, field.name)
    def getValues[T]: Traversable[Option[T]] = jsonsToValues[T](jsons, fieldType)

    def getRenderer[T]= {
      val typedFieldType = fieldType.asValueOf[T]

      { (value : Option[T]) =>
        value match {
          case Some(_) => typedFieldType.valueToDisplayString(value)
          case None => "Undefined"
        }
      }
    }

    fieldTypeId match {

      case FieldTypeId.String =>
        categoricalCountsWithFormatting(getValues[String], Some(getRenderer[String]))

      case FieldTypeId.Enum =>
        categoricalCountsWithFormatting(getValues[Int], Some(getRenderer[Int]))

      case FieldTypeId.Boolean =>
        categoricalCountsWithFormatting(getValues[Boolean], Some(getRenderer[Boolean]))

      case FieldTypeId.Double => {
        // TODO: use renderer here
        val renderer = getRenderer[Double]

        def outputLabel(value: BigDecimal) = value.setScale(1, RoundingMode.HALF_UP).toString

        val numCounts = numericalCounts(getValues[Double].flatten, 20, false, None, None)
        formatNumericalCounts(numCounts, Some(outputLabel))
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
        formatNumericalCounts(numCounts, outputLabel)
      }

      case FieldTypeId.Date => {
        val dates = getValues[ju.Date].flatten
        val values = dates.map(_.getTime)

        val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

        def formatDate(ms: BigDecimal) = dateFormat.format(new ju.Date(ms.toLongExact))

        val numCounts = numericalCounts(values, 20, false, None, None)
        formatNumericalCounts(numCounts, Some(formatDate))
      }

      case FieldTypeId.Null =>
        formatCategoricalCounts[Nothing](Seq((None, jsons.size)), None)

      // for the json type we can't do anything
      case FieldTypeId.Json =>
        Nil
    }
  }

  override def calcGroupedDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field
  ): Seq[(String, Seq[Count])] = {
    val groupFieldSpec = groupField.fieldTypeSpec
    val groupFieldType = ftf(groupFieldSpec)
    val groupFieldTypeId = groupFieldSpec.fieldType

    def groupValues[T]: Traversable[(Option[T], Traversable[JsObject])] = {
      val typedFieldType = groupFieldType.asValueOf[T]

      val groupedJsons = items.map { json =>
        val value = typedFieldType.jsonToValue(json \ groupField.name)
        (value, json)
      }.groupBy(_._1)

      groupedJsons.map { case (groupName, values) => (groupName, values.map(_._2))}
    }

    val groupedValues = groupFieldTypeId match {

      case FieldTypeId.String => {
        val groupJsons = groupValues[String].filter(_._1.isDefined).map {
          case (option, jsons) => (option.get, jsons)
        }.toSeq.sortBy(_._1)
        val undefinedGroupJsons = groupValues[String].find(_._1.isEmpty).map(_._2).getOrElse(Nil)
        groupJsons ++ Seq(("Undefined", undefinedGroupJsons))
      }

      case FieldTypeId.Enum => {
        val jsonsMap = groupValues[Int].toMap
        def getJsons(groupValue: Option[Int]) = jsonsMap.get(groupValue).getOrElse(Nil)

        val groupJsons = groupFieldSpec.enumValues.map(_.toSeq.sortBy(_._1).map { case (int, label) =>
          (label, getJsons(Some(int)))
        }).getOrElse(Nil)

        groupJsons ++ Seq(("Undefined", getJsons(None)))
      }

      case FieldTypeId.Boolean => {
        val jsonsMap = groupValues[Boolean].toMap
        def getJsons(groupValue: Option[Boolean]) = jsonsMap.get(groupValue).getOrElse(Nil)

        Seq(
          (groupFieldSpec.displayTrueValue.getOrElse("True"), getJsons(Some(true))),
          (groupFieldSpec.displayFalseValue.getOrElse("False"), getJsons(Some(false))),
          ("Undefined", getJsons(None))
        )
      }
    }

    groupedValues.map { case (groupName, jsons) =>
      val counts = calcDistributionCounts(jsons, field)
      (groupName, counts)
    }
  }

  private def categoricalCounts[T](
    values: Traversable[Option[T]]
  ): Seq[(Option[T], Int)] = {
    val countMap = MMap[Option[T], Int]()
    values.foreach { value =>
      val count = countMap.getOrElse(value, 0)
      countMap.update(value, count + 1)
    }
    countMap.toSeq
  }

  private def categoricalCountsRepo[T](
    fieldName: String,
    values: Traversable[T],
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]]
  ): Future[Seq[(Option[T], Int)]] = {
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

  private def numericalCounts[T: Numeric](
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

  private def numericalCountsRepo[T](
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

  override def categoricalCountsWithFormatting[T](
    values: Traversable[Option[T]],
    renderer: Option[Option[T] => String]
  ): Seq[Count] =
    formatCategoricalCounts(categoricalCounts(values), renderer)

  private def formatCategoricalCounts[T](
    counts: Seq[(Option[T], Int)],
    renderer: Option[Option[T] => String]
  ): Seq[Count] =
    counts.map {
      case (key, count) => {
        val stringKey = key.map(_.toString)
        val keyOrEmpty = stringKey.getOrElse("")
        val label = renderer.map(_.apply(key)).getOrElse(keyOrEmpty)

        Count(
          stringKey,
          count,
          label
        )
      }
    }

  private def formatNumericalCounts(
    counts: Seq[(BigDecimal, Int)],
    renderer: Option[BigDecimal => String] = None
  ): Seq[Count] =
    counts.sortBy(_._1).map {
      case (xValue, count) =>
        val xLabel = renderer.map(_.apply(xValue)).getOrElse(xValue.toString)

        Count(
          None,
          count,
          xLabel
        )
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
    groupField: Option[Field]
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

//  override def createBoxChartSpec(
//    items: Traversable[JsObject],
//    field: Field,
//    outputGridWidth: Option[Int] = None
//  ): Option[BoxChartSpec[_]] = {
//    val jsons = project(items, field.name)
//    val typeSpec = field.fieldTypeSpec
//    val fieldType = ftf(typeSpec)
//
//    def quantiles[T: Ordering](
//      toDouble: T => Double
//    ): Option[Quantiles[T]] =
//      BasicStats.quantiles[T](
//        jsonsToValues[T](jsons, fieldType).flatten.toSeq,
//        toDouble
//      )
//
//    def createChart[T: Ordering](quants: Option[Quantiles[T]]) =
//      quants.map(
//        BoxChartSpec(field.labelOrElseName, field.labelOrElseName, _, None, None, None, outputGridWidth)
//      )
//
//    typeSpec.fieldType match {
//      case FieldTypeId.Double => createChart(quantiles[Double](identity))
//
//      case FieldTypeId.Integer => createChart(quantiles[Long](_.toDouble))
//
//      case FieldTypeId.Date => createChart(quantiles[ju.Date](_.getTime.toDouble))
//
//      case _ => None
//    }
//  }

  override def createBoxChartSpec(
    items: Traversable[JsObject],
    field: Field,
    outputGridWidth: Option[Int] = None
  ): Option[BoxChartSpec[_]] =
    calcQuantiles(items, field).map { quants =>
      implicit val ordering = quants.ordering
      BoxChartSpec(field.labelOrElseName, field.labelOrElseName, quants, None, None, None, outputGridWidth)
    }

  override def createBoxChartSpecRepo(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    outputGridWidth: Option[Int] = None,
    height: Option[Int] = None
  ): Future[Option[BoxChartSpec[_]]] =
    for {
      quantiles <- calcQuantiles(dataRepo, criteria, field)
    } yield
      quantiles.map { quants =>
        implicit val ordering = quants.ordering
        BoxChartSpec(field.labelOrElseName, field.labelOrElseName, quants, None, None, height, outputGridWidth)
      }

  private def calcQuantiles(
    items: Traversable[JsObject],
    field: Field
  ): Option[Quantiles[Any]] = {
    val jsons = project(items, field.name)
    val typeSpec = field.fieldTypeSpec
    val fieldType = ftf(typeSpec)

    def quantiles[T: Ordering](
      toDouble: T => Double
    ): Option[Quantiles[Any]] =
      BasicStats.quantiles[T](
        jsonsToValues[T](jsons, fieldType).flatten.toSeq,
        toDouble
      ).asInstanceOf[Option[Quantiles[Any]]]

    typeSpec.fieldType match {
      case FieldTypeId.Double => quantiles[Double](identity)

      case FieldTypeId.Integer => quantiles[Long](_.toDouble)

      case FieldTypeId.Date => quantiles[ju.Date](_.getTime.toDouble)

      case _ => None
    }
  }

  private def calcQuantiles(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field
  ): Future[Option[Quantiles[Any]]] = {
    val typeSpec = field.fieldTypeSpec

    def quantiles[T: Ordering](toDouble: T => Double) =
      calcQuantiles[T](dataRepo, criteria, field, toDouble).map(
        _.asInstanceOf[Option[Quantiles[Any]]]
      )

    typeSpec.fieldType match {
      case FieldTypeId.Double => quantiles[Double](identity)

      case FieldTypeId.Integer => quantiles[Long](_.toDouble)

      case FieldTypeId.Date => quantiles[ju.Date](_.getTime.toDouble)

      case _ => Future(None)
    }
  }

  def calcQuantiles[T: Ordering](
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    toDouble: T => Double
  ): Future[Option[Quantiles[T]]] =
    for {
      // total length
      length <- dataRepo.count(criteria ++ Seq(field.name #!@))

      // create quantiles
      quants <-
        if (length > 0)
          createQuantilesAux(toDouble, length, dataRepo, criteria, field)
        else
          Future(None)
    } yield
      quants

  private def createQuantilesAux[T: Ordering](
    toDouble: T => Double,
    length: Int,                                            // must be non-zero
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field
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

  def jsonsToValues[T](
    jsons: Traversable[JsLookupResult],
    fieldType: FieldType[_]
  ): Traversable[Option[T]] =
    if (fieldType.spec.isArray) {
      val typedFieldType = fieldType.asValueOf[Array[Option[T]]]

      jsons.map( json =>
        typedFieldType.jsonToValue(json).map(_.toSeq).getOrElse(Seq(None))
      ).flatten
    } else {
      val typedFieldType = fieldType.asValueOf[T]

      jsons.map(typedFieldType.jsonToValue)
    }
}