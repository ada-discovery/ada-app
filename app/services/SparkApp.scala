package services

import javax.inject.Singleton

import com.stratio.datasource.mongodb.config.MongodbConfigBuilder
import com.stratio.datasource.mongodb._
import com.stratio.datasource.mongodb.config._
import com.stratio.datasource.mongodb.config.MongodbConfig._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SQLContext}
import org.apache.spark.{SparkContext, SparkConf}
import com.mongodb.casbah.{WriteConcern => MongodbWriteConcern}
import play.api.libs.json._

@Singleton
class SparkApp {

  private val conf = new SparkConf(false)
    .setMaster("local[*]")
    .setAppName("NCER-PD")
    .set("spark.logConf", "true")

  val sc = SparkContext.getOrCreate(conf)
  val sqlContext = new SQLContext(sc)
}