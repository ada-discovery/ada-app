package services

import javax.inject.{Inject, Singleton}

import org.apache.spark.sql.{DataFrame, Row, SQLContext, SparkSession}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.ml.linalg.{DenseVector, Vector, Vectors}
import org.apache.spark.sql.functions.{monotonically_increasing_id, struct, udf}
import org.apache.spark.sql.types.StructType
import play.api.Configuration
import play.api.libs.json._

import scala.util.Random

@Singleton
class SparkApp @Inject() (configuration: Configuration) {

  private val settings = Seq(
    "spark.executor.memory",
    "spark.network.timeout"
  ).flatMap( key =>
    configuration.getString(key).map((key, _))
  )

  private val conf = new SparkConf(false)
    .setMaster(configuration.getString("spark.master.url").getOrElse("local[*]"))
    .setAppName("Ada")
    .set("spark.logConf", "true")
    .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    .set("spark.worker.cleanup.enabled", "true")
    .set("spark.worker.cleanup.interval", "900")
    .setJars(configuration.getStringSeq("spark.driver.jars").getOrElse(Nil))
    .setAll(settings)

  val session = SparkSession
    .builder()
//    .appName("Spark SQL basic example")
//    .config("spark.some.config.option", "some-value")
    .config(conf)
    .getOrCreate()

  val sc = session.sparkContext

  val sqlContext = new SQLContext(sc)
}

object SparkUtil {
  import scala.collection.JavaConversions._

  def transposeVectors(
    session: SparkSession,
    columnNames: Traversable[String],
    df: DataFrame
  ): DataFrame = {
    def vectorElement(i: Int) = udf { r: Row =>
      r.getAs[Vector](0)(i)
//      } catch  {
//        case e => r.getList(0)(i)
//      }
    }

    // vectors for different columns are expected to have same size, otherwise transpose wouldn't work
    val vectorSize = df.select(columnNames.head).head().getAs[DenseVector](0).size

    val columns = columnNames.map { columnName =>
      for (i <- 0 until vectorSize) yield {
        vectorElement(i)(struct(columnName)).as(columnName + "_" + i)
      }
    }.flatten

    val newDf = df.select(columns.toSeq :_ *)

    val rows = for (i <- 0 until vectorSize) yield {
      val vectors = columnNames.map { columnName =>
        val values = newDf.select(columnName + "_" + i).collect().map(_.getDouble(0))
        Vectors.dense(values)
      }

      Row.fromSeq(vectors.toSeq)
    }

    val columnTypes = columnNames.map(columnName => df.schema(columnName))
    session.createDataFrame(rows, StructType(columnTypes.toSeq))
  }

  def joinByOrder(df1: DataFrame, df2: DataFrame) = {
    val joinColumnName = "_id" + Random.nextLong()

    // aux function
    def withOrderColumn(df: DataFrame) =
      df.withColumn(joinColumnName, monotonically_increasing_id())

    withOrderColumn(df1).join(withOrderColumn(df2), joinColumnName).drop(joinColumnName)
  }
}