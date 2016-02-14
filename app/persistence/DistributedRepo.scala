package persistence

import javax.inject.Inject

import com.stratio.datasource.mongodb.config.MongodbConfigBuilder
import com.stratio.datasource.mongodb._
import com.stratio.datasource.mongodb.config._
import com.stratio.datasource.mongodb.config.MongodbConfig._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SQLContext}
import org.apache.spark.{SparkContext, SparkConf}
import com.mongodb.casbah.{WriteConcern => MongodbWriteConcern}
import play.api.libs.json._
import services.SparkApp

import scala.reflect.ClassTag

trait DistributedRepo[E, ID] {
//  def get(id: ID): Option[E]

  def find(
    criteria: Option[String] = None,
    projection : Option[String] = None
  ): RDD[E]

  def save(entities: Seq[E]): Unit
}

class SparkMongoDistributedRepo[E <: scala.Product, ID](collectionName: String)(implicit ev : Format[E], ev2 : ClassTag[E]) extends DistributedRepo[E, ID] {

  @Inject var sparkApp : SparkApp = _

  val host = "localhost:27017"
  val database =  "highschool"
  lazy val sqlContext = sparkApp.sqlContext
  lazy val sc = sparkApp.sc

  override def find(criteria: Option[String], projection: Option[String]): RDD[E] = {
    val readConfig = MongodbConfigBuilder(Map(Host -> List(host), Database -> database, Collection -> collectionName, SamplingRatio -> 1.0, WriteConcern -> "normal"))
    val mongoRDD = sqlContext.fromMongoDB(readConfig.build())
    mongoRDD.registerTempTable(collectionName)
    val dataFrame = if (criteria.isDefined)
      mongoRDD.filter(criteria.get)
    else
      mongoRDD

    toJson(dataFrame).map(_.as[E])
  }

  private def toJson(df: DataFrame): RDD[JsObject] = {
    val fieldNames = df.schema.fieldNames
    df.map(row =>
      JsObject(fieldNames.zip(row.toSeq.map(value => JsString(value.toString))))
    )
  }

  override def save(entities: Seq[E]): Unit = {
    val saveConfig = MongodbConfigBuilder(Map(Host -> List(host), Database -> database, Collection -> collectionName, SamplingRatio -> 1.0, WriteConcern -> "normal", SplitSize -> 8, SplitKey -> "_id"))
    val dataFrame: DataFrame = sqlContext.createDataFrame(sc.parallelize[E](entities))
    dataFrame.saveToMongodb(saveConfig.build)
  }
}
