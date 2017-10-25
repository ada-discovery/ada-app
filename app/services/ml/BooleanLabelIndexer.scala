package services.ml

import java.util.UUID

import org.apache.spark.ml.Transformer
import org.apache.spark.ml.attribute.NominalAttribute
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.sql.{DataFrame, Dataset}
import org.apache.spark.sql.types.{BooleanType, StringType, StructType}

object BooleanLabelIndexer extends Transformer {

  private val indexer = new StringIndexer().setInputCol("label").setOutputCol("label_new_temp")

  override def transform(dataset: Dataset[_]): DataFrame =
    dataset.schema("label").dataType match {
      case BooleanType =>
        val newDf = dataset.withColumn("label", dataset("label").cast(StringType))
        indexer.fit(newDf).transform(newDf).drop("label").withColumnRenamed("label_new_temp", "label")

      case _ => dataset.asInstanceOf[DataFrame]
    }

  override def copy(extra: ParamMap): Transformer =
    this

  override def transformSchema(schema: StructType): StructType = {
    val attr = NominalAttribute.defaultAttr.withName("label")
    val outputFields = schema.fields.filter(_.name != "label") :+ attr.toStructField()
    StructType(outputFields)
  }

  override val uid: String =
    UUID.randomUUID.toString
}