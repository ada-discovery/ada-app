package persistence

import javax.inject.Inject

import com.stratio.datasource.mongodb.config.MongodbConfigBuilder
import models.Student
import play.api.Configuration
import scala.reflect.runtime.{ universe => ru}
import com.stratio.datasource.mongodb._
import com.stratio.datasource.mongodb.config.MongodbConfig._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Encoders, Dataset, DataFrame}
import services.SparkApp
import scala.reflect.ClassTag

trait DistributedRepo[E, ID] {
//  def get(id: ID): Option[E]

  def find(
    criteria: Option[String] = None,
    projectionColumns : Option[Seq[String]] = None,
    orderBy: Option[Seq[String]] = None
  ): Dataset[E]

//  def findJson(
//    criteria: Option[String] = None,
//    projectionColumns : Option[Seq[String]] = None,
//    orderBy: Option[Seq[String]] = None
//  ): Dataset[JsObject]

  def save(entities: Seq[E]): Unit

//  def saveJson(entities: Seq[JsObject]): Unit

  def deleteAll : Unit
}

protected class SparkMongoDistributedRepo[E <: scala.Product, ID](collectionName: String)(implicit ev : ClassTag[E], ev2: ru.TypeTag[E]) extends DistributedRepo[E, ID] {

  private implicit val encoder = Encoders.product[E]

  @Inject var sparkApp: SparkApp = _
  @Inject var configuration: Configuration = _

  lazy val host = configuration.getString("mongodb.host").get
  lazy val database =  configuration.getString("mongodb.db").get
  lazy val sqlContext = sparkApp.sqlContext
  lazy val sc = sparkApp.sc

  override def find(
    criteria: Option[String],
    projection: Option[Seq[String]],
    orderBy: Option[Seq[String]]
  ): Dataset[E] = {
    val readConfig = MongodbConfigBuilder(Map(Host -> List(host), Database -> database, Collection -> collectionName, SamplingRatio -> 1.0, WriteConcern -> "normal"))
    val dataFrame = sqlContext.fromMongoDB(readConfig.build())
    dataFrame.createOrReplaceTempView(collectionName)

    val criteriaDataFrame = criteria match {
      case Some(criteria) => dataFrame.filter(criteria)
      case None => dataFrame
    }

    val projectedDataFrame = projection match {
      case Some(projection) => criteriaDataFrame.select(projection.head, projection.tail:_*)
      case None => criteriaDataFrame
    }

    val orderedDataFrame = orderBy match {
      case Some(orderBy) => projectedDataFrame.orderBy(orderBy.head, orderBy.tail:_*)
      case None => projectedDataFrame
    }

    orderedDataFrame.as[E]
//    dataFrame.as[E]
//    toJson(dataFrame2) // .map(_.as[E])
  }

//  override def findJson(
//    criteria: Option[String],
//    projectionColumns : Option[Seq[String]],
//    orderBy: Option[Seq[String]]
//  ) =
//    toJson(find(criteria, projectionColumns, orderBy))

  override def save(entities: Seq[E]): Unit = {
    val saveConfig = MongodbConfigBuilder(Map(Host -> List(host), Database -> database, Collection -> collectionName, SamplingRatio -> 1.0, WriteConcern -> "normal", SplitSize -> 8, SplitKey -> "_id"))
    val dataFrame: DataFrame = sqlContext.createDataFrame(sc.parallelize[E](entities))
    dataFrame.saveToMongodb(saveConfig.build)
  }

//  override def saveJson(entities: Seq[JsObject]): Unit = {
//    val saveConfig = MongodbConfigBuilder(Map(Host -> List(host), Database -> database, Collection -> collectionName, SamplingRatio -> 1.0, WriteConcern -> "normal", SplitSize -> 8, SplitKey -> "_id"))
//    val jsonStringRDD = sc.parallelize(entities).map(jsObject => Json.stringify(jsObject))
//    val dataFrame: DataFrame = sqlContext.read.json(jsonStringRDD)
//    dataFrame.saveToMongodb(saveConfig.build)
//  }

  override def deleteAll: Unit = {
    sqlContext.sql(s"DROP TABLE IF EXISTS $database.$collectionName")
  }

//  private def toJson(df: DataFrame)(implicit encoder: Encoders.bean[Student]) : Dataset[JsObject] = {
//    val fieldNames = df.schema.fieldNames
//    df.as[Student]
//    df.map(row =>
//      JsObject(fieldNames.zip(row.toSeq.map(value => JsString(value.toString))))
//    )
//  }
}

//object SparkHelper {
//  def toFormatted[E](rdd : Dataset[JsObject], reads : JsValue => JsResult[E])(implicit ev2 : ClassTag[E]): Dataset[E] = {
//    rdd.flatMap { record =>
//      val a = reads(record).asOpt
//      println(a)
//      a
//    }
//  }
//}