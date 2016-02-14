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

object SparkCommons {

  def saveToMongo(host : String, database : String, collection : String)(dataFrame: DataFrame) {
    val saveConfig = MongodbConfigBuilder(Map(Host -> List(host), Database -> database, Collection -> collection, SamplingRatio -> 1.0, WriteConcern -> "normal", SplitSize -> 8, SplitKey -> "_id"))
    dataFrame.saveToMongodb(saveConfig.build)
  }

  def loadFromMongo(sqlContext: SQLContext)(host : String, database : String, collection : String)(sqlStatement : String) : DataFrame = {
    val readConfig = MongodbConfigBuilder(Map(Host -> List(host), Database -> database, Collection -> collection, SamplingRatio -> 1.0, WriteConcern -> "normal"))
    val mongoRDD = sqlContext.fromMongoDB(readConfig.build())
    mongoRDD.registerTempTable(collection)
    sqlContext.sql(sqlStatement)
  }

  def loadAllFromMongo(sqlContext: SQLContext)(host : String, database : String, collection : String) : DataFrame =
    loadFromMongo(sqlContext)(host, database, collection)("SELECT * FROM " + collection)

  def toJson(df: DataFrame): RDD[JsObject] = {
    val fieldNames = df.schema.fieldNames
    df.map(row =>
      JsObject(fieldNames.zip(row.toSeq.map(value => JsString(value.toString))))
    )
  }
}

@Singleton
class SparkApp {

  private val conf = new SparkConf(false)
    .setMaster("local[*]")
    .setAppName("NCER-PD")
    .set("spark.logConf", "true")

  val sc = SparkContext.getOrCreate(conf)
  val sqlContext = new SQLContext(sc)
}