package services

import javax.inject.{Inject, Singleton}

import org.apache.spark.sql.{DataFrame, Row, SQLContext, SparkSession}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.sql.functions.{monotonically_increasing_id, struct, udf}
import org.apache.spark.sql.types.StructType
import play.api.Configuration
import play.api.libs.json._
import scala.reflect.ClassTag
import org.apache.spark.sql.Encoders

import scala.util.Random

@Singleton
class SparkApp @Inject() (configuration: Configuration) {

  private val reservedKeys = Set("spark.master.url", "spark.driver.jars")

  private val settings = configuration.keys.filter(key =>
    key.startsWith("spark.") && !reservedKeys.contains(key)
  ).flatMap { key =>
    println(key)
    configuration.getString(key).map((key, _))
  }

  private val conf = new SparkConf(false)
    .setMaster(configuration.getString("spark.master.url").getOrElse("local[*]"))
    .setAppName("Ada")
    .set("spark.logConf", "true")
    .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    .set("spark.worker.cleanup.enabled", "true")
    .set("spark.worker.cleanup.interval", "900")
    .setJars(configuration.getStringSeq("spark.driver.jars").getOrElse(Nil))
    .setAll(settings)
    .registerKryoClasses(Array(
      classOf[scala.collection.mutable.ArraySeq[_]]
    ))

  val session = SparkSession
    .builder()
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
    val vectorSize = df.select(columnNames.head).head().getAs[Vector](0).size

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

  implicit def kryoEncoder[A](implicit ct: ClassTag[A]) = Encoders.kryo[A](ct)
}