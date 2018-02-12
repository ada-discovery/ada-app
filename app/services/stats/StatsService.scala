package services.stats

import java.{util => ju}
import javax.inject.{Inject, Singleton}

import _root_.util.{GrouppedVariousSize, _}
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape}
import com.google.inject.ImplementedBy
import dataaccess.Criterion.Infix
import dataaccess.JsonUtil.project
import dataaccess._
import models._
import org.apache.spark.ml.feature.ChiSqSelector
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.ml.stat.ChiSquareTest
import org.apache.spark.sql.DataFrame
import play.api.Logger
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import services.ml.BooleanLabelIndexer
import services.stats.calc._
import services.{FeaturesDataFrameFactory, SparkApp}

import JsonFieldUtil._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode
import scala.collection.JavaConversions._

@ImplementedBy(classOf[StatsServiceImpl])
trait StatsService {

  def calcDistributionCounts(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    numericBinCount: Option[Int]
   ): Future[Traversable[Count[_]]]

  def calcDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    numericBinCount: Option[Int]
  ): Traversable[Count[_]]

  def calcGroupedDistributionCounts(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Field,
    numericBinCount: Option[Int]
  ): Future[Seq[(String, Traversable[Count[_]])]]

  def calcGroupedDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field,
    numericBinCount: Option[Int]
  ): Seq[(String, Traversable[Count[_]])]

  def calcCumulativeCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field]
  ): Seq[(String, Traversable[Count[Any]])]

  // quartiles

  def calcQuartiles(
    items: Traversable[JsObject],
    field: Field
  ): Option[Quartiles[Any]]

  def calcQuartiles(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field
  ): Future[Option[Quartiles[Any]]]

  // scatter

  def collectScatterData(
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field]
  ): Traversable[(String, Traversable[(Any, Any)])]

  // correlations

  def calcPearsonCorrelations(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    fields: Seq[Field],
    parallelism: Option[Int] = None,
    withProjection: Boolean = true,
    areValuesAllDefined: Boolean = false
  ): Future[Seq[Seq[Option[Double]]]]

  def calcPearsonCorrelations(
    items: Traversable[JsObject],
    fields: Seq[Field]
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
  private implicit val ftf = FieldTypeHelper.fieldTypeFactory()
  private val defaultNumericBinCount = 20
  private val anovaTest = new CommonsOneWayAnovaAdjusted()

  private val logger = Logger

  override def calcDistributionCounts(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    numericBinCountOption: Option[Int]
  ): Future[Traversable[Count[_]]] = {
    val spec = field.fieldTypeSpec
    val fieldType = ftf(spec)
    val fieldTypeId = spec.fieldType
    val numericBinCount = numericBinCountOption.getOrElse(defaultNumericBinCount)

    fieldTypeId match {
      case FieldTypeId.String =>
        for {
          jsons <- dataRepo.find(criteria = criteria, projection = Seq(field.name))
        } yield {
          val typedFieldType = fieldType.asValueOf[String]
          val values = jsons.map(json => typedFieldType.jsonToValue(json \ field.name))
          val counts = UniqueDistributionCountsCalc[String].fun()(values)

          createStringCounts(counts, fieldType.asValueOf[String])
        }

      case FieldTypeId.Enum => {
        val values = spec.enumValues.map(_.map(_._1).toSeq.sorted).getOrElse(Nil)
        for {
          counts <- categoricalCountsRepo(field.name, values, dataRepo, criteria)
        } yield
          createStringCounts(counts, fieldType.asValueOf[Int])
      }

      case FieldTypeId.Boolean => {
        val values = Seq(true, false)
        for {
          counts <- categoricalCountsRepo(field.name, values, dataRepo, criteria)
        } yield
          createStringCounts(counts, fieldType.asValueOf[Boolean])
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
          createStringCounts[Nothing](Seq((None, count)), fieldType.asValueOf[Nothing])

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
  ): Future[Seq[(String, Traversable[Count[_]])]] = {

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
  ): Traversable[Count[_]] = {
    val spec = field.fieldTypeSpec
    val fieldType = ftf(spec)

    val numericBinCount = numericBinCountOption.getOrElse(defaultNumericBinCount)

    def getValues[T]: Traversable[Option[T]] = {
      val jsonToValue = jsonToArrayValue[T](field)
      items.map(jsonToValue).flatten
    }

    def calcUniqueCounts[T] = {
      val counts = UniqueDistributionCountsCalc[T].fun()(getValues[T])
      createStringCounts(counts, fieldType.asValueOf[T])
    }

    spec.fieldType match {
      case FieldTypeId.String | FieldTypeId.Enum | FieldTypeId.Boolean => calcUniqueCounts

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
        createStringCounts[Nothing](Seq((None, items.size)), fieldType.asValueOf[Nothing])

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
  ): Seq[(String, Traversable[Count[_]])] = {
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

  private def createStringCounts[T](
    counts: Traversable[(Option[T], Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map {
      case (value, count) => {
        val stringKey = value.map(_.toString)
        val keyOrEmpty = stringKey.getOrElse("")
        val label = value.map(value => fieldType.valueToDisplayString(Some(value))).getOrElse("Undefined")

        Count(label, count, stringKey)
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
  ): Traversable[(String, Traversable[(Any, Any)])] = {
    // create a json value converter
    val jsonValues = jsonToTuple[Any, Any](xField, yField)
    groupField.map { groupField =>
      // create a group->string converter and merge with the value one
      val groupJsonString = jsonToDisplayString(groupField)
      val groupStringJsonValues = {jsObject: JsObject =>
        val values = jsonValues(jsObject)
        (groupJsonString(jsObject), values._1, values._2)
      }

      // extract data and produce scatter data
      val data = xyzItems.map(groupStringJsonValues)
      val result = GroupScatterCalc[String, Any, Any].fun()(data)

      result.map { case (groupName, values) => (groupName.getOrElse("Undefined"), values)}
    }.getOrElse{
      val data = xyzItems.map(jsonValues)
      val result = ScatterCalc[Any, Any].fun()(data)
      Seq(("all", result))
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

  override def calcQuartiles(
    items: Traversable[JsObject],
    field: Field
  ): Option[Quartiles[Any]] = {
    // helper function to convert jsons to values and calculate quartiles
    def quartiles[T: Ordering](toDouble: T => Double): Option[Quartiles[Any]] = {
      val jsonToValue = jsonToArrayValue[T](field)
      val values = items.map(jsonToValue).flatten.flatten
      QuartilesCalc[T].fun(toDouble)(values).asInstanceOf[Option[Quartiles[Any]]]
    }

    field.fieldType match {
      case FieldTypeId.Double => quartiles[Double](identity)
      case FieldTypeId.Integer => quartiles[Long](_.toDouble)
      case FieldTypeId.Date => quartiles[ju.Date](_.getTime.toDouble)
      case _ => None
    }
  }

  override def calcQuartiles(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field
  ): Future[Option[Quartiles[Any]]] = {
    val typeSpec = field.fieldTypeSpec

    def quartiles[T: Ordering](toDouble: T => Double) =
      calcQuartiles[T](dataRepo, criteria, field, toDouble).map(
        _.asInstanceOf[Option[Quartiles[Any]]]
      )

    typeSpec.fieldType match {
      case FieldTypeId.Double => quartiles[Double](identity)
      case FieldTypeId.Integer => quartiles[Long](_.toDouble)
      case FieldTypeId.Date => quartiles[ju.Date](_.getTime.toDouble)
      case _ => Future(None)
    }
  }

  def calcQuartiles[T: Ordering](
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    field: Field,
    toDouble: T => Double
  ): Future[Option[Quartiles[T]]] =
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
  ): Future[Option[Quartiles[T]]] = {
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
        Quartiles(lowerWhisker, lowerQuantile, median, upperQuantile, upperWhisker)
      }
    }
  }

  //////////////////
  // Correlations //
  //////////////////

  override def calcPearsonCorrelations(
    items: Traversable[JsObject],
    fields: Seq[Field]
  ): Seq[Seq[Option[Double]]] = {
    val doubleValues = jsonToDoubles(fields)
    PearsonCorrelationCalc.fun()(items.map(doubleValues))
  }

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private def ssad() = {
    val topHeadSink = Sink.head[Int]
    val bottomHeadSink = Sink.head[Int]
    val sharedDoubler = Flow[Int].map(_ * 2)

    val lala = RunnableGraph.fromGraph(GraphDSL.create(topHeadSink, bottomHeadSink)((_, _)) { implicit builder =>
      (topHS, bottomHS) =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[Int](2))
        Source.single(1) ~> broadcast.in

        broadcast.out(0) ~> sharedDoubler ~> topHS.in
        broadcast.out(1) ~> sharedDoubler ~> bottomHS.in
        ClosedShape
    })
    lala.run()
  }

  private def connectSinksToSource[T, U](
    source: Source[T, _],
    sinks: Traversable[Sink[U, _]]
  ) = {
    val topHeadSink = Sink.head[Int]
    val bottomHeadSink = Sink.head[Int]
    val sharedDoubler = Flow[Int].map(_ * 2)

    val lala = RunnableGraph.fromGraph(GraphDSL.create(topHeadSink, bottomHeadSink)((_, _)) { implicit builder =>
      (topHS, bottomHS) =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[Int](2))
        Source.single(1) ~> broadcast.in

        broadcast.out(0) ~> sharedDoubler ~> topHS.in
        broadcast.out(1) ~> sharedDoubler ~> bottomHS.in
        ClosedShape
    })
    lala.run()
  }

  private def ddasdsa() = {
    val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val in = Source(1 to 10)
      val out = Sink.ignore

      val bcast = builder.add(Broadcast[Int](2))
      val merge = builder.add(Merge[Int](2))

      val f1, f2, f3, f4 = Flow[Int].map(_ + 10)

      in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
      bcast ~> f4 ~> merge
      ClosedShape
    })
  }

  override def calcPearsonCorrelations(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    fields: Seq[Field],
    parallelism: Option[Int],
    withProjection: Boolean,
    areValuesAllDefined: Boolean
  ): Future[Seq[Seq[Option[Double]]]] =
    for {
      // create a data source
      source <- dataRepo.findAsStream(criteria, Nil, if (withProjection) fields.map(_.name) else Nil)

      // covert the data source to (double) value source and calc correlations
      corrs <- if (areValuesAllDefined) {
        val doubleValues = jsonToDoublesDefined(fields)
        calcPearsonCorrelationsAllDefined(source.map(doubleValues), fields.size, parallelism)
      } else {
        val doubleValues = jsonToDoubles(fields)
        calcPearsonCorrelations(source.map(doubleValues), fields.size, parallelism)
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
        val sink = PearsonCorrelationCalc.sink(featuresNum, groupSizes)
        source.runWith(sink)
      }
    } yield
      PearsonCorrelationCalc.postSink(groupSizes)(accums)
  }

  override def calcPearsonCorrelationsAllDefined(
    source: Source[Seq[Double], _],
    featuresNum: Int,
    parallelism: Option[Int]
  ): Future[Seq[Seq[Option[Double]]]] = {
    val groupSizes = calcGroupSizes(featuresNum, parallelism)

    for {
      // run the stream with a selected sink and get accums back
      globalAccum <- {
        val sink = PearsonCorrelationAllDefinedCalc.sink(featuresNum, groupSizes)
        source.runWith(sink)
      }
    } yield
      PearsonCorrelationAllDefinedCalc.postSink(groupSizes)(globalAccum)
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