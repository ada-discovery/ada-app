package services

import com.google.inject.ImplementedBy
import dataaccess._
import models._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import _root_.util.BasicStats.Quantiles
import _root_.util._
import _root_.util.GrouppedVariousSize
import dataaccess.JsonUtil.project
import java.{util => ju}
import javax.inject.{Inject, Singleton}

import Criterion.Infix
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.apache.commons.math3.stat.inference.{OneWayAnova, TestUtils}
import org.apache.spark.ml.feature.ChiSqSelector
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.ml.stat.ChiSquareTest
import org.apache.spark.sql.DataFrame
import play.api.Logger
import services.ml.BooleanLabelIndexer

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[StatsServiceImpl])
trait StatsService {

  def calcDistributionCounts(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    numericBinCount: Option[Int]
   ): Future[Seq[Count[_]]]

  def calcDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    numericBinCount: Option[Int]
  ): Seq[Count[_]]

  def calcGroupedDistributionCounts(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Field,
    numericBinCount: Option[Int]
  ): Future[Seq[(String, Seq[Count[_]])]]

  def calcGroupedDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field,
    numericBinCount: Option[Int]
  ): Seq[(String, Seq[Count[_]])]

  def categoricalCountsWithFormatting[T](
    values: Traversable[Option[T]],
    renderer: Option[Option[T] => String]
  ): Seq[Count[String]]

  def calcCumulativeCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field]
  ): Seq[(String, Seq[Count[Any]])]

  def calcQuantiles(
    items: Traversable[JsObject],
    field: Field
  ): Option[Quantiles[Any]]

  def calcQuantiles(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field
  ): Future[Option[Quantiles[Any]]]

  def collectScatterData(
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field]
  ): Seq[(String, Seq[(Any, Any)])]

  // correlations

  def calcPearsonCorrelations(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    fields: Seq[Field],
    parallelism: Option[Int] = None,
    areValuesAllDefined: Boolean = false
  ): Future[Seq[Seq[Option[Double]]]]

  def calcPearsonCorrelations(
    items: Traversable[JsObject],
    fields: Seq[Field]
  ): Seq[Seq[Option[Double]]]

  def calcPearsonCorrelations(
    values: Traversable[Seq[Option[Double]]]
  ): Seq[Seq[Option[Double]]]

  def calcPearsonCorrelations(
    source: Source[Seq[Option[Double]], _],
    featuresNum: Int,
    parallelism: Option[Int]
  ): Future[Seq[Seq[Option[Double]]]]

  def calcPearsonCorrelationsAllDefined(
    source: Source[Seq[Double], _],
    featuresNum: Int,
    parallelism: Option[Int]
  ): Future[Seq[Seq[Option[Double]]]]

  // independence tests

  def testChiSquare(
    data: Traversable[JsObject],
    inputFields: Seq[Field],
    targetField: Field
  ): Seq[ChiSquareResult]

  def testOneWayAnova(
    items: Traversable[JsObject],
    inputFields: Seq[Field],
    targetField: Field
  ): Seq[Option[AnovaResult]]

  def testIndependence(
    items: Traversable[JsObject],
    inputFields: Seq[Field],
    targetField: Field
  ): Seq[Option[Either[ChiSquareResult, AnovaResult]]]

  def testIndependenceSorted(
    items: Traversable[JsObject],
    inputFields: Seq[Field],
    targetField: Field
  ): Seq[(Field, Option[Either[ChiSquareResult, AnovaResult]])]

  def selectFeaturesAsChiSquare(
    data: DataFrame,
    featuresToSelectNum: Int
  ): DataFrame

  def selectFeaturesAsChiSquare(
    data: Traversable[JsObject],
    inputAndOutputFields: Seq[Field],
    outputFieldName: String,
    featuresToSelectNum: Int,
    discretizerBucketsNum: Int
  ): Traversable[String]

  def selectFeaturesAsAnovaChiSquare(
    data: Traversable[JsObject],
    inputFields: Seq[Field],
    targetField: Field,
    featuresToSelectNum: Int
  ): Seq[Field]
}

@Singleton
class StatsServiceImpl @Inject() (sparkApp: SparkApp) extends StatsService {

  private val session = sparkApp.session
  private val ftf = FieldTypeHelper.fieldTypeFactory()
  private val defaultNumericBinCount = 20
  private val anovaTest = new CommonsOneWayAnovaAdjusted()

  private val logger = Logger

  override def calcDistributionCounts(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    numericBinCountOption: Option[Int]
  ): Future[Seq[Count[_]]] = {
    val fieldTypeSpec = field.fieldTypeSpec
    val fieldType = ftf(fieldTypeSpec)
    val fieldTypeId = fieldTypeSpec.fieldType
    val numericBinCount = numericBinCountOption.getOrElse(defaultNumericBinCount)

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

      case FieldTypeId.Double =>
        for {
          numCounts <-
            numericalCountsRepo(
              BigDecimal(_: Double), _.toDouble,
              field.name, fieldType.asValueOf[Double], dataRepo, criteria, numericBinCount, false, None, None
            )
        } yield
          convertNumericalCounts(numCounts, None)

      case FieldTypeId.Integer =>
        for {
          numCounts <-
            numericalCountsRepo(
              BigDecimal(_: Long), _.toDouble,
              field.name, fieldType.asValueOf[Long], dataRepo, criteria, numericBinCount, true, None, None
            )
        } yield {
          // TODO: fix this
          val convert = None
//            if (numCounts.length < 20)
//              Some { value: BigDecimal => value.toLong }
//            else
//              None

          convertNumericalCounts(numCounts, convert)
        }

      case FieldTypeId.Date => {
        def convert(ms: BigDecimal) = new ju.Date(ms.setScale(0, BigDecimal.RoundingMode.CEILING).toLongExact)

        for {
          numCounts <-
            numericalCountsRepo(
              {x : ju.Date => BigDecimal(x.getTime)},
              convert,
              field.name, fieldType.asValueOf[ju.Date], dataRepo, criteria, numericBinCount, false, None, None
            )
        } yield
          convertNumericalCounts(numCounts, Some(convert(_)))
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
    groupField: Field,
    numericBinCountOption: Option[Int]
  ): Future[Seq[(String, Seq[Count[_]])]] = {

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
            val values = jsons.flatMap(json => typedFieldType.jsonToValue(json \ groupField.name))
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

        case FieldTypeId.Json =>
          for {
            jsons <- dataRepo.find(criteria = criteria, projection = Seq(groupField.name))
          } yield {
            val typedFieldType = groupFieldType.asValueOf[JsObject]
            val values = jsons.flatMap(json => typedFieldType.jsonToValue(json \ groupField.name))
            values.toSet.toSeq.map { json: JsObject => (Json.stringify(json), json) }.sortBy(_._1)
          }
      }

      seriesCounts <- {
        val groupFieldName = groupField.name
        val countFutures = groupLabelValues.par.map { case (label, value) =>
          val finalCriteria = criteria ++ Seq(groupFieldName #== value)
          calcDistributionCounts(dataRepo, finalCriteria, field, numericBinCountOption).map { counts =>
            (label, counts)
          }
        }.toList

        val undefinedGroupCriteria = criteria ++ Seq(groupFieldName #=@)
        val naValueFuture = calcDistributionCounts(dataRepo, undefinedGroupCriteria, field, numericBinCountOption).map { counts =>
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
    field: Field,
    numericBinCountOption: Option[Int]
  ): Seq[Count[_]] = {
    val fieldTypeSpec = field.fieldTypeSpec
    val fieldType = ftf(fieldTypeSpec)
    val fieldTypeId = fieldTypeSpec.fieldType
    val numericBinCount = numericBinCountOption.getOrElse(defaultNumericBinCount)

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

      case FieldTypeId.Double =>
        val numCounts = numericalCounts(getValues[Double].flatten, numericBinCount, false, None, None)
        convertNumericalCounts(numCounts, None)

      case FieldTypeId.Integer => {
        val values = getValues[Long].flatten
        val min = if (values.nonEmpty) values.min else 0
        val max = if (values.nonEmpty) values.max else 0
        val valueCount = Math.abs(max - min)

        // TODO: fix this
        val convert = None
//          if (valueCount < numericBinCount)
//            Some { value: BigDecimal => value.toLong }
//          else
//            None

        val numCounts = numericalCounts(values, Math.min(numericBinCount, valueCount + 1).toInt, valueCount < numericBinCount, None, None)
        convertNumericalCounts(numCounts, convert)
      }

      case FieldTypeId.Date => {
        val dates = getValues[ju.Date].flatten
        val values = dates.map(_.getTime)

        def convert(ms: BigDecimal) = new ju.Date(ms.setScale(0, BigDecimal.RoundingMode.CEILING).toLongExact)

        val numCounts = numericalCounts(values, numericBinCount, false, None, None)
        convertNumericalCounts(numCounts, Some(convert(_)))
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
    groupField: Field,
    numericBinCountOption: Option[Int]
  ): Seq[(String, Seq[Count[_]])] = {
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

      case FieldTypeId.String =>
        val groupedJsons = groupValues[String].filter(_._1.isDefined).map {
          case (option, jsons) => (option.get, jsons)
        }.toSeq.sortBy(_._1)
        val undefinedGroupJsons = groupValues[String].find(_._1.isEmpty).map(_._2).getOrElse(Nil)
        groupedJsons ++ Seq(("Undefined", undefinedGroupJsons))

      case FieldTypeId.Enum =>
        val jsonsMap = groupValues[Int].toMap
        def getJsons(groupValue: Option[Int]) = jsonsMap.get(groupValue).getOrElse(Nil)

        val groupedJsons = groupFieldSpec.enumValues.map(_.toSeq.sortBy(_._1).map { case (int, label) =>
          (label, getJsons(Some(int)))
        }).getOrElse(Nil)

        groupedJsons ++ Seq(("Undefined", getJsons(None)))

      case FieldTypeId.Boolean =>
        val jsonsMap = groupValues[Boolean].toMap
        def getJsons(groupValue: Option[Boolean]) = jsonsMap.get(groupValue).getOrElse(Nil)

        Seq(
          (groupFieldSpec.displayTrueValue.getOrElse("True"), getJsons(Some(true))),
          (groupFieldSpec.displayFalseValue.getOrElse("False"), getJsons(Some(false))),
          ("Undefined", getJsons(None))
        )

      case FieldTypeId.Json =>
        val values = groupValues[JsObject]
        val groupedJsons = values.collect { case (Some(value), jsons) => (Json.stringify(value), jsons)}.toSeq.sortBy(_._1)
        val undefinedGroupJsons = values.find(_._1.isEmpty).map(_._2).getOrElse(Nil)
        groupedJsons ++ Seq(("Undefined", undefinedGroupJsons))
    }

    groupedValues.map { case (groupName, jsons) =>
      val counts = calcDistributionCounts(jsons, field, numericBinCountOption)
      (groupName, counts)
    }
  }

  private def categoricalCounts[T](
    values: Traversable[Option[T]]
  ): Seq[(Option[T], Int)] =
    values.groupBy(identity).map { case (value, seq) => (value, seq.size) }.toSeq

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

      val bucketIndeces = doubles.map { value =>
        if (stepSize.equals(BigDecimal(0)))
          0
        else if (value == max)
          columnCount - 1
        else
          ((value - min) / stepSize).setScale(0, RoundingMode.FLOOR).toInt
      }

      val countMap = bucketIndeces.groupBy(identity).map { case (index, values) => (index, values.size) }

      (0 until columnCount).map { index =>
        val count = countMap.get(index).getOrElse(0)
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
  ): Seq[Count[String]] =
    formatCategoricalCounts(categoricalCounts(values), renderer)

  private def formatCategoricalCounts[T](
    counts: Seq[(Option[T], Int)],
    renderer: Option[Option[T] => String]
  ): Seq[Count[String]] = {
    counts.map {
      case (key, count) => {
        val stringKey = key.map(_.toString)
        val keyOrEmpty = stringKey.getOrElse("")
        val label = renderer.map(_.apply(key)).getOrElse(keyOrEmpty)

        Count(label, count, stringKey)
      }
    }
  }

  private def convertNumericalCounts[T](
    counts: Seq[(BigDecimal, Int)],
    convert: Option[BigDecimal => T] = None
  ): Seq[Count[_]] = {
    val sum = counts.map(_._2).sum

    counts.sortBy(_._1).map {
      case (xValue, count) =>
        val convertedValue = convert.map(_.apply(xValue)).getOrElse(xValue.toDouble)

        Count(convertedValue, count, None)
    }
  }

  override def collectScatterData(
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

    def values(
      jsons: Seq[JsReadable],
      fieldType: FieldType[_]
    ) =
      jsons.map(fieldType.jsonToValue)

    val xValues = values(xJsons, xFieldType)
    val yValues = values(yJsons, yFieldType)

    def flattenTupples[A, B](
      tupples: Traversable[(Option[A], Option[B])]
    ): Seq[(A, B)] =
      tupples.map(_.zipped).flatten.toSeq

    groupField match {
      case Some(groupField) =>
        val groupFieldType = ftf(groupField.fieldTypeSpec)
        val groupJsons = project(xyzSeq, groupField.name).toSeq
        val groupValues = jsonsToDisplayString(groupFieldType, groupJsons)

        val groupedValues = (groupValues, xValues, yValues).zipped.groupBy(_._1).map { case (groupValue, values) =>
          (
            groupValue,
            flattenTupples(values.map(tupple => (tupple._2, tupple._3)))
          )
        }
        groupedValues.filter(_._2.nonEmpty).toSeq

      case None =>
        val xys = flattenTupples(xValues.zip(yValues))
        Seq(("all", xys))
    }
  }

  override def calcCumulativeCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field]
  ): Seq[(String, Seq[Count[Any]])] = {
    def calcCounts[T: Ordering] = calcCumulativeCountsAux[T](items, field, groupField)

    field.fieldType match {

      case FieldTypeId.String => calcCounts[String]

      case FieldTypeId.Enum => calcCounts[Int]

      case FieldTypeId.Boolean => calcCounts[Boolean]

      case FieldTypeId.Double => calcCounts[Double]

      case FieldTypeId.Integer => calcCounts[Long]

      case FieldTypeId.Date => calcCounts[ju.Date]

      case _ => Nil
    }
  }

  private def calcCumulativeCountsAux[T: Ordering](
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field]
  ): Seq[(String, Seq[Count[T]])] = {
    val fieldType = ftf(field.fieldTypeSpec).asInstanceOf[FieldType[T]]

    val itemsSeq = items.toSeq
    val jsons = project(itemsSeq, field.name).toSeq
    val values = jsons.map(fieldType.jsonToValue)

    def sortValues(groupedValues: Seq[(String, Seq[T])]) =
      groupedValues.map{ case (name, values) =>
        val counts = (values.sorted, Stream.from(1)).zipped.map { case (value, count) =>
          Count(value, count, None)
        }
        (name, counts)
      }

    groupField match {
      case Some(groupField) =>
        val groupFieldType = ftf(groupField.fieldTypeSpec)
        val groupJsons = project(itemsSeq, groupField.name).toSeq
        val groups = jsonsToDisplayString(groupFieldType, groupJsons)

        val groupedValues = (groups, values).zipped.groupBy(_._1).map { case (group, values) =>
          (group, values.map(_._2).flatten.toSeq)
        }.toSeq
        sortValues(groupedValues)

      case None =>
        sortValues(Seq(("all", values.flatten)))
    }
  }

  private def jsonsToDisplayString[T](
    fieldType: FieldType[T],
    jsons: Traversable[JsReadable]
  ): Traversable[String] =
    jsons.map { json =>
      fieldType.jsonToValue(json) match {
        case None => "Undefined"
        case Some(value) => fieldType.valueToDisplayString(Some(value))
      }
    }

  override def calcQuantiles(
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

  override def calcQuantiles(
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

  override def calcPearsonCorrelations(
    items: Traversable[JsObject],
    fields: Seq[Field]
  ): Seq[Seq[Option[Double]]] = {

    def getValues[T](field: Field): Traversable[Option[T]] = {
      val typedFieldType = ftf(field.fieldTypeSpec).asValueOf[T]
      project(items, field.name).map(typedFieldType.jsonToValue)
    }

    val fieldsWithValues: Seq[(Field, Traversable[Option[Double]])] = fields.map { field =>
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

    val data: Seq[Seq[Option[Double]]] = fieldsWithValues.map(_._2).transpose

    calcPearsonCorrelations(data)
  }

  override def calcPearsonCorrelations(
    values: Traversable[Seq[Option[Double]]]
  ): Seq[Seq[Option[Double]]] = {
    val elementsCount = if (values.nonEmpty) values.head.size else 0

    def calc(index1: Int, index2: Int) = {
      val els = (values.map(_ (index1)).toSeq, values.map(_ (index2)).toSeq).zipped.flatMap {
        case (el1: Option[Double], el2: Option[Double]) =>
          if ((el1.isDefined) && (el2.isDefined)) {
            Some((el1.get, el2.get))
          } else
            None
      }

      if (els.nonEmpty) {
        val length = els.size

        val mean1 = els.map(_._1).sum / length
        val mean2 = els.map(_._2).sum / length

        // sum up the squares
        val mean1Sq = els.map(_._1).foldLeft(0.0)(_ + Math.pow(_, 2)) / length
        val mean2Sq = els.map(_._2).foldLeft(0.0)(_ + Math.pow(_, 2)) / length

        // sum up the products
        val pMean = els.foldLeft(0.0) { case (accum, pair) => accum + pair._1 * pair._2 } / length

        // calculate the pearson score
        val numerator = pMean - mean1 * mean2

        val denominator = Math.sqrt(
          (mean1Sq - Math.pow(mean1, 2)) * (mean2Sq - Math.pow(mean2, 2))
        )
        if (denominator == 0)
          None
        else
          Some(numerator / denominator)
      } else
        None
    }

    (0 until elementsCount).par.map { i =>
      (0 until elementsCount).par.map { j =>
        if (i != j)
          calc(i, j)
        else
          Some(1d)
      }.toList
    }.toList
  }

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  case class PersonIterativeAccum(
    sum1: Double,
    sum2: Double,
    sqSum1: Double,
    sqSum2: Double,
    pSum: Double,
    count: Int
  )

  private def pearsonCorrelationSink(
    n: Int,
    parallelGroupSizes: Seq[Int]
  ) =
    Sink.fold[Seq[Seq[PersonIterativeAccum]], Seq[Option[Double]]](
      for (i <- 0 to n - 1) yield Seq.fill(i)(PersonIterativeAccum(0, 0, 0, 0, 0, 0))
    ) {
      case (accums, featureValues) =>

        def calcAux(accumFeatureValuePairs: Seq[(Seq[PersonIterativeAccum], Option[Double])]) =
          accumFeatureValuePairs.map { case (rowAccums, value1) =>
            rowAccums.zip(featureValues).map { case (accum, value2) =>
              if (value1.isDefined && value2.isDefined) {
                PersonIterativeAccum(
                  accum.sum1 + value1.get,
                  accum.sum2 + value2.get,
                  accum.sqSum1 + value1.get * value1.get,
                  accum.sqSum2 + value2.get * value2.get,
                  accum.pSum + value1.get * value2.get,
                  accum.count + 1
                )
              } else
                accum
            }
          }

        val accumFeatureValuePairs = accums.zip(featureValues)

        parallelGroupSizes match {
          case Nil => calcAux(accumFeatureValuePairs)
          case _ => accumFeatureValuePairs.grouped(parallelGroupSizes).toSeq.par.flatMap(calcAux).toList
        }
    }

  case class PersonIterativeAccumGlobal(
    sumSqSums: Seq[(Double, Double)],
    pSums: Seq[Seq[Double]],
    count: Int
  )

  private def pearsonCorrelationSinkAllDefined(
    n: Int,
    parallelGroupSizes: Seq[Int]
  ) =
    Sink.fold[PersonIterativeAccumGlobal, Seq[Double]](
      PersonIterativeAccumGlobal(
        sumSqSums = for (i <- 0 to n - 1) yield (0d, 0d),
        pSums = for (i <- 0 to n - 1) yield Seq.fill(i)(0d),
        count = 0
      )
    ) {
      case (accumGlobal, featureValues) =>
        val newSumSqSums = accumGlobal.sumSqSums.zip(featureValues).map { case ((sum, sqSum), value) =>
          (sum + value, sqSum + value * value)
        }

        def calcAux(pSumValuePairs: Seq[(Seq[Double], Double)]) =
          pSumValuePairs.map { case (pSums, value1) =>
            pSums.zip(featureValues).map { case (pSum, value2) =>
              pSum + value1 * value2
            }
          }

        val pSumValuePairs = accumGlobal.pSums.zip(featureValues)

        val newPSums = parallelGroupSizes match {
          case Nil => calcAux(pSumValuePairs)
          case _ => pSumValuePairs.grouped(parallelGroupSizes).toSeq.par.flatMap(calcAux).toList
        }
        PersonIterativeAccumGlobal(newSumSqSums, newPSums, accumGlobal.count + 1)
    }

  override def calcPearsonCorrelations(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    fields: Seq[Field],
    parallelism: Option[Int],
    areValuesAllDefined: Boolean
  ): Future[Seq[Seq[Option[Double]]]] =
    for {
      // create a data source
      source <- dataRepo.findAsStream(criteria, Nil, fields.map(_.name))

      // covert the data source to (double) value source and calc correlations
      corrs <- if (areValuesAllDefined) {
        val getDoubleValuesDefined = {json: JsObject => fields.map(field => getDoubleValue(field)(json).get)}
        calcPearsonCorrelationsAllDefined(source.map(getDoubleValuesDefined), fields.size, parallelism)
      } else {
        val getDoubleValues = {json: JsObject => fields.map(field => getDoubleValue(field)(json))}
        calcPearsonCorrelations(source.map(getDoubleValues), fields.size, parallelism)
      }
    } yield
      corrs

  override def calcPearsonCorrelations(
    source: Source[Seq[Option[Double]], _],
    featuresNum: Int,
    parallelism: Option[Int]
  ): Future[Seq[Seq[Option[Double]]]] = {
    val groupSizes = calcGroupSizes(featuresNum, parallelism)

    for {
      // run the stream with a selected sink and get accums back
      accums <- {
        val sink = pearsonCorrelationSink(featuresNum, groupSizes)
        source.runWith(sink)
      }
    } yield
      accumsToCorrelations(accums, groupSizes)
  }

  override def calcPearsonCorrelationsAllDefined(
    source: Source[Seq[Double], _],
    featuresNum: Int,
    parallelism: Option[Int]
  ): Future[Seq[Seq[Option[Double]]]] = {
    val groupSizes = calcGroupSizes(featuresNum, parallelism)

    for {
      // run the stream with a selected sink and get accums back
      accums <- {
        val sink = pearsonCorrelationSinkAllDefined(featuresNum, groupSizes)
        source.runWith(sink).map(globalAccumToAccums)
      }
    } yield
      accumsToCorrelations(accums, groupSizes)
  }

  private def calcGroupSizes(n: Int, parallelism: Option[Int]) =
    parallelism.map { groupNumber =>
      val initGroupSize = n / Math.sqrt(groupNumber)

      val groupSizes = (1 to groupNumber).map { i =>
        val doubleGroupSize = (Math.sqrt(i) - Math.sqrt(i - 1)) * initGroupSize
        Math.round(doubleGroupSize).toInt
      }.filter(_ > 0)

      val sum = groupSizes.sum
      val fixedGroupSizes = if (sum < n) {
        if (groupSizes.size > 1)
          groupSizes.take(groupSizes.size - 1) :+ (groupSizes.last + (n - sum))
        else if (groupSizes.size == 1)
          Seq(groupSizes.head + (n - sum))
        else
          Seq(n)
      } else
        groupSizes

      val newSum = fixedGroupSizes.sum
      logger.info("Groups          : " + fixedGroupSizes.mkString(","))
      logger.info("Sum             : " + newSum)

      fixedGroupSizes
    }.getOrElse(
      Nil
    )

  private def accumToCorrelation(accum: PersonIterativeAccum): Option[Double] = {
    val length = accum.count

    if (length < 2) {
      None
    } else {
      val mean1 = accum.sum1 / length
      val mean2 = accum.sum2 / length

      // sum up the squares
      val mean1Sq = accum.sqSum1 / length
      val mean2Sq = accum.sqSum2 / length

      // sum up the products
      val pMean = accum.pSum / length

      // calculate the pearson score
      val numerator = pMean - mean1 * mean2

      val denominator = Math.sqrt((mean1Sq - mean1 * mean1) * (mean2Sq - mean2 * mean2))

      if (denominator == 0)
        None
      else if (denominator.isNaN || denominator.isInfinity) {
        logger.error(s"Got not-a-number denominator during a correlation calculation.")
        None
      } else if (numerator.isNaN || numerator.isInfinity) {
        logger.error(s"Got not-a-number numerator during a correlation calculation.")
        None
      } else
        Some(numerator/ denominator)
    }
  }

  private def globalAccumToAccums(
    globalAccum: PersonIterativeAccumGlobal
  ): Seq[Seq[PersonIterativeAccum]] = {
    logger.info("Converting the global streamed accumulator to the partial ones.")
    globalAccum.pSums.zip(globalAccum.sumSqSums).map { case (rowPSums, (sum1, sqSum1)) =>
      rowPSums.zip(globalAccum.sumSqSums).map { case (pSum, (sum2, sqSum2)) =>
        PersonIterativeAccum(sum1, sum2, sqSum1, sqSum2, pSum, globalAccum.count)
      }
    }
  }

  private def accumsToCorrelations(
    accums: Seq[Seq[PersonIterativeAccum]],
    parallelGroupSizes: Seq[Int]
  ): Seq[Seq[Option[Double]]] = {
    logger.info("Creating correlations from the streamed accumulators.")
    val n = accums.size

    def calcAux(accums: Seq[Seq[PersonIterativeAccum]]) = accums.map(_.map(accumToCorrelation))

    val triangleResults = parallelGroupSizes match {
      case Nil => calcAux(accums)
      case _ => accums.grouped(parallelGroupSizes).toSeq.par.flatMap(calcAux).toList
    }

    for (i <- 0 to n - 1) yield
      for (j <- 0 to n - 1) yield {
        if (i > j)
          triangleResults(i)(j)
        else if (i < j)
          triangleResults(j)(i)
        else
          Some(1d)
      }
  }

  private def getDoubleValue(field: Field)(json: JsObject): Option[Double] = {
    // helper function
    def value[T]: Option[T] = {
      val typedFieldType = ftf(field.fieldTypeSpec).asValueOf[T]
      typedFieldType.jsonToValue(json \ field.name)
    }

    field.fieldType match {
      case FieldTypeId.Double => value[Double]

      case FieldTypeId.Integer => value[Long].map(_.toDouble)

      case FieldTypeId.Date => value[java.util.Date].map(_.getTime.toDouble)

      case _ => None
    }
  }


  override def testChiSquare(
    data: Traversable[JsObject],
    inputFields: Seq[Field],
    targetField: Field
  ): Seq[ChiSquareResult] = {
    val fieldTypeSpces = (inputFields ++ Seq(targetField)).map(field => (field.name, field.fieldTypeSpec))

    // prepare the features->label data frame
    val df = FeaturesDataFrameFactory(session, data, fieldTypeSpces, Some(targetField.name), Some(10), true)

    val inputDf = BooleanLabelIndexer.transform(df)

    // run the chi-square independence test
    val results = testChiSquareAux(inputDf)

    // collect the results in the order prescribed by the inout fields sequence
    val featureNames = inputDf.columns.filterNot(columnName => columnName.equals("features") || columnName.equals("label"))
    val featureNameResultMap = featureNames.zip(results).toMap
    inputFields.map(field =>
      featureNameResultMap.get(field.name).get
    )
  }

  private def testChiSquareAux(
    df: DataFrame
  ): Seq[ChiSquareResult] = {
    val resultDf = ChiSquareTest.test(df, "features", "label")

    val chi = resultDf.head

    val pValues = chi.getAs[Vector](0).toArray.toSeq
    val degreesOfFreedom = chi.getSeq[Int](1)
    val statistics = chi.getAs[Vector](2).toArray.toSeq

    (pValues, degreesOfFreedom, statistics).zipped.map(
      ChiSquareResult(_, _, _)
    )
  }

  override def testOneWayAnova(
    items: Traversable[JsObject],
    inputFields: Seq[Field],
    targetField: Field
  ): Seq[Option[AnovaResult]] = {

    val fieldNameTypeMap: Map[String, FieldType[_]] = (inputFields ++ Seq(targetField)).map { field => (field.name, ftf(field.fieldTypeSpec))}.toMap

    def doubleValue[T](fieldName: String, json: JsObject): Option[Double] = {
      val fieldType: FieldType[_] = fieldNameTypeMap.get(fieldName).getOrElse(throw new IllegalArgumentException(s"Field name $fieldName not found."))

      fieldType.spec.fieldType match {
        case FieldTypeId.Double => fieldType.asValueOf[Double].jsonToValue(json \ fieldName)

        case FieldTypeId.Integer => fieldType.asValueOf[Long].jsonToValue(json \ fieldName).map(_.toDouble)

        case FieldTypeId.Date => fieldType.asValueOf[ju.Date].jsonToValue(json \ fieldName).map(_.getTime.toDouble)

        case _ => None
      }
    }

    val labeledFeatures = items.map { json =>
      val features = inputFields.map(field => doubleValue(field.name, json))
      val label = fieldNameTypeMap.get(targetField.name).get.jsonToDisplayString(json \ targetField.name)
      (label, features)
    }

    anovaTestAux(labeledFeatures)
  }

  private def anovaTestAux(
    labeledValues: Traversable[(String, Seq[Option[Double]])]
  ): Seq[Option[AnovaResult]] = {
    val featuresNum = labeledValues.head._2.size

    val groupedLabelFeatureValues = labeledValues.toGroupMap.map {
      case (label, values) => (label, values.toSeq.transpose)
    }.toSeq

    (0 until featuresNum).map { featureIndex =>
      val featureValues: Seq[Array[Double]] =
        groupedLabelFeatureValues.map { case (_, featureValues) =>
          featureValues(featureIndex).flatten.toArray[Double]
        }

//      println("Feature: " + featureIndex)
//      featureValues.foreach(array => println(array.mkString(", ")))
//      println

      if (featureValues.size > 1 && featureValues.forall(_.size > 1)) {
        val anovaStats = anovaTest.anovaStats(featureValues)
        val pValue = anovaTest.anovaPValue(anovaStats)
        Some(AnovaResult(pValue, anovaStats.F, anovaStats.dfbg, anovaStats.dfwg))
      } else
        None
    }
  }

  def jsonsToValues[T](
    jsons: Traversable[JsReadable],
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

  override def testIndependenceSorted(
    items: Traversable[JsObject],
    inputFields: Seq[Field],
    targetField: Field
  ): Seq[(Field, Option[Either[ChiSquareResult, AnovaResult]])] = {
    val results = testIndependence(items, inputFields, targetField)

    // Sort and combine the results
    def pValueAndStat(result: Option[Either[ChiSquareResult, AnovaResult]]): (Double, Double) =
      result.map {
        _ match {
          case Left(chiSquareResult) => (chiSquareResult.pValue, chiSquareResult.statistics)
          case Right(anovaResult) => (anovaResult.pValue, anovaResult.fValue)
        }
      }.getOrElse((Double.PositiveInfinity, 0d))

    inputFields.zip(results).sortWith { case ((fieldName1, result1), (fieldName2, result2)) =>
      val (pValue1, stat1) = pValueAndStat(result1)
      val (pValue2, stat2) = pValueAndStat(result2)

      (pValue1 < pValue2) || (pValue1 == pValue2 && stat1 > stat2)
    }
  }

  override def testIndependence(
    items: Traversable[JsObject],
    inputFields: Seq[Field],
    targetField: Field
  ): Seq[Option[Either[ChiSquareResult, AnovaResult]]] = {
    // ANOVA
    val numericalTypes = Seq(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Date)
    val numericalInputFields = inputFields.filter(field => numericalTypes.contains(field.fieldTypeSpec.fieldType))
    val anovaResults = testOneWayAnova(items, numericalInputFields, targetField)
    val anovaFieldNameResultMap = numericalInputFields.map(_.name).zip(anovaResults).toMap

    // Chi-Square
    val categoricalTypes = Seq(FieldTypeId.Enum, FieldTypeId.String, FieldTypeId.Boolean, FieldTypeId.Json)
    val categoricalInputFields = inputFields.filter(field => categoricalTypes.contains(field.fieldTypeSpec.fieldType))
    val chiSquareResults = testChiSquare(items, categoricalInputFields, targetField)
    val chiSquareFieldNameResultMap = categoricalInputFields.map(_.name).zip(chiSquareResults).toMap

    inputFields.map { field =>
      val name = field.name

      chiSquareFieldNameResultMap.get(name) match {
        case Some(chiSquareResult) => Some(Left(chiSquareResult))
        case None => anovaFieldNameResultMap.get(name).flatMap {
          case Some(anovaResult) => Some(Right(anovaResult))
          case None => None
        }
      }
    }
  }

  override def selectFeaturesAsAnovaChiSquare(
    data: Traversable[JsObject],
    inputFields: Seq[Field],
    targetField: Field,
    featuresToSelectNum: Int
  ): Seq[Field] = {
    val results = testIndependenceSorted(data, inputFields, targetField)
    results.map(_._1).take(featuresToSelectNum)
  }

  override def selectFeaturesAsChiSquare(
    data: DataFrame,
    featuresToSelectNum: Int
  ): DataFrame = {
    val model = selectFeaturesAsChiSquareModel(data, featuresToSelectNum)

    model.transform(data)
  }

  override def selectFeaturesAsChiSquare(
    data: Traversable[JsObject],
    inputAndOutputFields: Seq[Field],
    outputFieldName: String,
    featuresToSelectNum: Int,
    discretizerBucketsNum: Int
  ): Traversable[String] = {
    val fieldNameSpecs = inputAndOutputFields.map(field => (field.name, field.fieldTypeSpec))
    val df = FeaturesDataFrameFactory(session, data, fieldNameSpecs, Some(outputFieldName), Some(discretizerBucketsNum))
    val inputDf = BooleanLabelIndexer.transform(df)

    // get the Chi-Square model
    val model = selectFeaturesAsChiSquareModel(inputDf, featuresToSelectNum)

    // extract the features
    val featureNames = inputDf.columns.filterNot(columnName => columnName.equals("features") || columnName.equals("label"))
    model.selectedFeatures.map(featureNames(_))
  }

  private def selectFeaturesAsChiSquareModel(
    data: DataFrame,
    featuresToSelectNum: Int
  ) = {
    val selector = new ChiSqSelector()
      .setFeaturesCol("features")
      .setLabelCol("label")
      .setOutputCol("selectedFeatures")
      .setNumTopFeatures(featuresToSelectNum)

    selector.fit(data)
  }
}

case class ChiSquareResult(pValue: Double, degreeOfFreedom: Int, statistics: Double)

object ChiSquareResult {
  implicit val chiSquareResultFormat = Json.format[ChiSquareResult]
}

case class AnovaResult(pValue: Double, fValue: Double, degreeOfFreedomBetweenGroups: Int, degreeOfFreedomWithinGroups: Int)

object AnovaResult {
  implicit val anovaResultFormat = Json.format[AnovaResult]
}
