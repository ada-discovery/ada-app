package services

import javax.inject.Singleton

import com.stratio.datasource.mongodb.config.MongodbConfigBuilder
import com.stratio.datasource.mongodb._
import com.stratio.datasource.mongodb.config._
import com.stratio.datasource.mongodb.config.MongodbConfig._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SQLContext, SparkSession}
import org.apache.spark.{SparkConf, SparkContext}
import com.mongodb.casbah.{WriteConcern => MongodbWriteConcern}
import play.api.libs.json._

@Singleton
class SparkApp {

  private val conf = new SparkConf(false)
    .setMaster("local[*]")
    .setAppName("NCER-PD")
    .set("spark.logConf", "true")
    .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")

  val session = SparkSession
    .builder()
//    .appName("Spark SQL basic example")
//    .config("spark.some.config.option", "some-value")
    .config(conf)
    .getOrCreate()

  val sc = SparkContext.getOrCreate(conf)
  val sqlContext = new SQLContext(sc)
}