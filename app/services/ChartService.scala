package services

import java.text.SimpleDateFormat

import com.google.inject.ImplementedBy
import dataaccess._
import models._
import play.api.libs.json.JsObject
import util.BasicStats.Quantiles
import util._
import util.JsonUtil.project
import java.{util => ju}

import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode

@ImplementedBy(classOf[ChartServiceImpl])
trait ChartService {

  def createDistributionChartSpecs(
    items: Traversable[JsObject],
    fieldChartTypes: Traversable[FieldChartType],
    fields: Traversable[Field],
    showLabels: Boolean = false,
    showLegend: Boolean = true
  ): Traversable[(String, ChartSpec)]

  def createDistributionChartSpec(
    items: Traversable[JsObject],
    chartType: Option[ChartType.Value],
    fieldOrName: Either[Field, String],
    showLabels: Boolean = false,
    showLegend: Boolean = true
  ): ChartSpec

  def createBoxChartSpec(
    items: Traversable[JsObject],
    field: Field
  ): Option[ChartSpec]

  def createScatterChartSpec(
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field]
  ): ScatterChartSpec
}

class ChartServiceImpl extends ChartService {

  private val ftf = FieldTypeHelper.fieldTypeFactory

  override def createDistributionChartSpecs(
    items: Traversable[JsObject],
    fieldChartTypes: Traversable[FieldChartType],
    fields: Traversable[Field],
    showLabels: Boolean = false,
    showLegend: Boolean = true
  ): Traversable[(String, ChartSpec)] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap

    def createChartSpecAux(
      chartType: Option[ChartType.Value],
      fieldOrName: Either[Field, String]
    ) = createDistributionChartSpec(items, chartType, fieldOrName, showLabels, showLegend)

    fieldChartTypes.map { case FieldChartType(fieldName, chartType) =>
      val chartSpec = nameFieldMap.get(fieldName).fold(
        createChartSpecAux(chartType, Right(fieldName))
      )(field =>
        createChartSpecAux(chartType, Left(field))
      )
      (fieldName, chartSpec)
    }
  }

  override def createDistributionChartSpec(
    items: Traversable[JsObject],
    chartType: Option[ChartType.Value],
    fieldOrName: Either[Field, String],
    showLabels: Boolean = false,
    showLegend: Boolean = true
  ): ChartSpec = {
    val (fieldName, chartTitle, enumMap, fieldTypeSpec) = fieldOrName match {
      case Left(field) => (
        field.name,
        field.label.getOrElse(fieldLabel(field.name)),
        field.numValues,
        field.fieldTypeSpec
      )

      // failover... no corresponding field, providing default values instead
      case Right(fieldName) => (
        fieldName,
        fieldLabel(fieldName),
        None,
        FieldTypeSpec(FieldTypeId.String, false)
      )
    }

    val jsons = project(items, fieldName)
    val fieldType = ftf(fieldTypeSpec)
    val fieldTypeId = fieldTypeSpec.fieldType

    def getValues[T]: Traversable[Option[T]] = {
      val typedFieldType = fieldType.asValueOf[T]
      jsons.map(typedFieldType.jsonToValue)
    }

    fieldTypeId match {
      case FieldTypeId.String =>
        ChartSpec.categorical(
          getValues[String], enumMap, chartTitle, showLabels, showLegend, chartType
        )

      case FieldTypeId.Enum =>
        ChartSpec.categorical(
          getValues[Int], enumMap, chartTitle, showLabels, showLegend, chartType
        )

      case FieldTypeId.Boolean =>
        ChartSpec.categorical(
          getValues[Boolean], enumMap, chartTitle, showLabels, showLegend, chartType
        )

      case FieldTypeId.Double => {
        def outputLabel(value: BigDecimal) = value.setScale(1, RoundingMode.HALF_UP).toString

        ChartSpec.numerical(
          getValues[Double].flatten, fieldName, chartTitle, 20, false, None, None, chartType, Some(outputLabel)
        )
      }

      case FieldTypeId.Integer => {
        val values = getValues[Long].flatten
        val min = values.min
        val max = values.max
        val valueCount = max - min

        def outputLabel(value: BigDecimal) = value.toInt.toString

        ChartSpec.numerical(
          values,
          fieldName,
          chartTitle,
          Math.min(20, valueCount + 1).toInt,
          valueCount < 20,
          None, None, chartType,
          if (valueCount < 20)
            Some(outputLabel)
          else
            None
        )
      }

      case FieldTypeId.Date => {
        val dates = getValues[ju.Date].flatten
        val values = dates.map(_.getTime)

        val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
        def formatDate(ms: BigDecimal) = dateFormat.format(new ju.Date(ms.toLongExact))

        ChartSpec.numerical(values, fieldName, chartTitle, 20, false, None, None, chartType, Some(formatDate))
      }

      // for null and json types we can't show anything
      case FieldTypeId.Null | FieldTypeId.Json =>
        ChartSpec.categorical(
          Nil, enumMap, chartTitle, showLabels, showLegend, chartType
        )
    }
  }

  override def createScatterChartSpec(
    xyzItems: Traversable[JsObject],
    xField: Field,
    yField: Field,
    groupField: Option[Field]
  ): ScatterChartSpec = {
    val data = getScatterData(xyzItems, xField, yField, groupField)
    ScatterChartSpec(
      "Comparison",
      xField.labelOrElseName,
      yField.labelOrElseName,
      data.map{ case (name, values) =>
        val initName = if (name.isEmpty) "Undefined" else name
        (initName, "rgba(223, 83, 83, .5)", values.map(pair => Seq(pair._1, pair._2)))
      }
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

  // Box chart can be generated only for numeric typesL Double, Integer
  override def createBoxChartSpec(
    items: Traversable[JsObject],
    field: Field
  ): Option[ChartSpec] = {
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

      case _ =>
        None
    }

    quants.map( quant => BoxChartSpec(field.labelOrElseName + " Box", quant))
  }
}