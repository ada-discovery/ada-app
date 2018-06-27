package services.ml

import java.{util => ju}

import dataaccess.{FieldType, FieldTypeHelper}
import models.{FieldTypeId, FieldTypeSpec}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.ml.feature.{QuantileDiscretizer, StringIndexer, VectorAssembler}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import play.api.libs.json.JsObject

import scala.util.Random

object FeaturesDataFrameFactory {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  def apply(
    session: SparkSession,
    jsons: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    featureFieldNames: Seq[String]
  ): DataFrame = {
    // convert jsons to a data frame
    val fieldNameAndTypes = fields.map { case (name, fieldTypeSpec) => (name, ftf(fieldTypeSpec))}
    val stringFieldNames = fields.filter {_._2.fieldType == FieldTypeId.String }.map(_._1)
    val stringFieldsNotToIndex = stringFieldNames.diff(featureFieldNames).toSet
    val df = jsonsToDataFrame(session)(jsons, fieldNameAndTypes, stringFieldsNotToIndex)

    df.transform(
      prepFeaturesDataFrame(featureFieldNames.toSet, None)
    )
  }

  def apply(
    session: SparkSession,
    jsons: Traversable[JsObject],
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: Option[String],
    discretizerBucketNum: Option[Int] = None,
    dropNaValues: Boolean = true
  ): DataFrame = {
    // convert jsons to a data frame
    val fieldNameAndTypes = fields.map { case (name, fieldTypeSpec) => (name, ftf(fieldTypeSpec))}
    val df = jsonsToDataFrame(session)(jsons, fieldNameAndTypes)

    // prep the features of the data frame
    val featureNames = featureFieldNames(fields, outputFieldName)

    val numericFieldNames = fields.flatMap { case (name, fieldTypeSpec) =>
      if (featureNames.contains(name)) {
        if (fieldTypeSpec.fieldType == FieldTypeId.Integer || fieldTypeSpec.fieldType == FieldTypeId.Double)
          Some(name)
        else
          None
      } else
        None
    }

    val discretizedDf = discretizerBucketNum.map( discretizerBucketNum =>
      numericFieldNames.foldLeft(df) { case (newDf, fieldName) =>
        discretizeAsQuantiles(newDf, discretizerBucketNum, fieldName)
      }
    ).getOrElse(df)

    discretizedDf.transform(
      prepFeaturesDataFrame(featureNames, outputFieldName, dropNaValues)
    )
  }

  def apply(
    session: SparkSession)(
    json: JsObject,
    inputSeriesFieldPaths: Seq[String],
    outputSeriesFieldPath: String,
    dlSize: Int,
    predictAhead: Int
  ): DataFrame = {
    val values: Seq[Seq[Double]] =
      IOSeriesUtil.applyJson(json, inputSeriesFieldPaths, outputSeriesFieldPath).map { case (input, output) =>
        createDelayLine(input, output.drop(dlSize + predictAhead - 1), dlSize)
      }.getOrElse(Nil)

    val valueBroadVar = session.sparkContext.broadcast(values)
    val size = values.size

    val data: RDD[Row] = session.sparkContext.range(0, size).map { index =>
      if (index == 0)
        println(s"Creating Spark rows from a broadcast variable of size ${size}")
      Row.fromSeq(valueBroadVar.value(index.toInt))
    }

    val inputFieldNames =
      (1 to dlSize).flatMap( i =>
        inputSeriesFieldPaths.map( fieldName =>
          fieldName.replaceAllLiterally(".", "_") + "_" + i
        )
      )

    val outputFieldName = outputSeriesFieldPath.replaceAllLiterally(".", "_")

    val structTypes = (inputFieldNames ++ Seq(outputFieldName)).map(StructField(_, DoubleType, true))

    val df = session.createDataFrame(data, StructType(structTypes))

    df.cache()

    df.transform(
      prepFeaturesDataFrame(inputFieldNames.toSet, Some(outputFieldName), true)
    )
  }

  private def featureFieldNames(
    fields: Seq[(String, FieldTypeSpec)],
    outputFieldName: Option[String]
  ): Set[String] =
    outputFieldName.map( outputName =>
      fields.map(_._1).filterNot(_ == outputName)
    ).getOrElse(
      fields.map(_._1)
    ).toSet

  def prepFeaturesDataFrame(
    featureFieldNames: Set[String],
    outputFieldName: Option[String],
    dropNaValues: Boolean = true)(
    df: DataFrame
  ): DataFrame = {
    // drop null values
    val nonNullDf = if (dropNaValues) df.na.drop else df

    val assembler = new VectorAssembler()
      .setInputCols(nonNullDf.columns.filter(featureFieldNames.contains))
      .setOutputCol("features")

    val featuresDf = assembler.transform(nonNullDf)

    outputFieldName.map(
      featuresDf.withColumnRenamed(_, "label")
    ).getOrElse(
      featuresDf
    )
  }

  private def jsonsToDataFrame(
    session: SparkSession)(
    jsons: Traversable[JsObject],
    fieldNameAndTypes: Seq[(String, FieldType[_])],
    stringFieldsNotToIndex: Set[String] = Set()
  ): DataFrame = {
    val (df, broadcastVar) = jsonsToDataFrameAux(session)(jsons, fieldNameAndTypes, stringFieldsNotToIndex)

    df.cache()
    broadcastVar.unpersist()
    df
  }

  private def jsonsToDataFrameAux(
    session: SparkSession)(
    jsons: Traversable[JsObject],
    fieldNameAndTypes: Seq[(String, FieldType[_])],
    stringFieldsNotToIndex: Set[String] = Set()
  ): (DataFrame, Broadcast[_]) = {
    val values = jsons.toSeq.map( json =>
      fieldNameAndTypes.map { case (fieldName, fieldType) =>
        fieldType.spec.fieldType match {
          case FieldTypeId.Enum =>
            fieldType.jsonToDisplayString(json \ fieldName)

          case FieldTypeId.Date =>
            fieldType.asValueOf[ju.Date].jsonToValue(json \ fieldName).map(date => new java.sql.Date(date.getTime)).getOrElse(null)

          case _ =>
            fieldType.jsonToValue(json \ fieldName).getOrElse(null)
        }
      }
    )

    val valueBroadVar = session.sparkContext.broadcast(values)
    val size = values.size

    val data: RDD[Row] = session.sparkContext.range(0, size).map { index =>
      if (index == 0)
        println(s"Creating Spark rows from a broadcast variable of size ${size}")
      Row.fromSeq(valueBroadVar.value(index.toInt))
    }

    data.cache()

    val structTypes = fieldNameAndTypes.map { case (fieldName, fieldType) =>
      val sparkFieldType: DataType = fieldType.spec.fieldType match {
        case FieldTypeId.Integer => LongType
        case FieldTypeId.Double => DoubleType
        case FieldTypeId.Boolean => BooleanType
        case FieldTypeId.Enum => StringType
        case FieldTypeId.String => StringType
        case FieldTypeId.Date => DateType
        case FieldTypeId.Json => NullType // TODO
        case FieldTypeId.Null => NullType
      }

      // TODO: we can perhaps create our own metadata for enums without StringIndexer
      //      val jsonMetadata = Json.obj("ml_attr" -> Json.obj(
      //        "vals" -> JsArray(Seq(JsString("lala"), JsString("lili"))),
      //        "type" -> "nominal"
      //      ))
      //      val metadata = Metadata.fromJson(Json.stringify(jsonMetadata))

      StructField(fieldName, sparkFieldType, true)
    }

    val stringTypes = structTypes.filter(_.dataType.equals(StringType))

    //    df = session.createDataFrame(rdd_of_rows)
    val df = session.createDataFrame(data, StructType(structTypes))

    val finalDf = stringTypes.foldLeft(df){ case (newDf, stringType) =>
      if (!stringFieldsNotToIndex.contains(stringType.name)) {
        val randomIndex = Random.nextLong()
        val indexer = new StringIndexer().setInputCol(stringType.name).setOutputCol(stringType.name + randomIndex)
        indexer.fit(newDf).transform(newDf).drop(stringType.name).withColumnRenamed(stringType.name + randomIndex, stringType.name)
      } else {
        newDf
      }
    }

    (finalDf, valueBroadVar)
  }

  private def discretizeAsQuantiles(
    df: DataFrame,
    bucketsNum: Int,
    columnName: String
  ): DataFrame = {
    val outputColumnName = columnName + "_" + Random.nextLong()
    val discretizer = new QuantileDiscretizer()
      .setInputCol(columnName)
      .setOutputCol(outputColumnName)
      .setNumBuckets(bucketsNum)

    val result = discretizer.fit(df).transform(df)

    result.drop(columnName).withColumnRenamed(outputColumnName, columnName)
  }

  def createDelayLine[T](
    inputStream: Seq[Seq[T]],
    outputStream: Seq[T],
    dlSize : Int
  ): Seq[Seq[T]] = {
    val inputDelayLineStream = inputStream.sliding(dlSize).toStream.map(x => x.flatten)
    inputDelayLineStream.zip(outputStream).map { case (inputs, output) =>
      inputs ++ Seq(output)
    }
  }
}

object Lala extends App {
  private val size = 20
  private val featuresNum = 3
  private val dlSize = 4

  private val inputs: Seq[Seq[Int]] =
    for (_ <- 0 to size - 1) yield
      for (_ <- 0 to featuresNum - 1) yield Random.nextInt(10)

  private val outputs: Seq[Int] =
    for (_ <- 0 to size) yield Random.nextInt(100)

  val dlFeatures = FeaturesDataFrameFactory.createDelayLine(inputs, outputs, dlSize)

  println("Inputs:\n")
  inputs.transpose.foreach( values =>
    println(values.mkString(","))
  )

  println
  println("Outputs:\n")
  println(outputs.mkString(","))
  println

  println("DL Features:\n")
  dlFeatures.foreach( values =>
    println(values.mkString(","))
  )
}