package runnables

import javax.inject.Inject

import org.apache.spark.sql._
import persistence.DistributedRepo
import reactivemongo.bson.BSONObjectID
import services.SparkApp
import services.SparkCommons.{saveToMongo, loadAllFromMongo, toJson}

case class Student(_id : Option[String], name: String, age: Int)

object Student {
  implicit val StudentFormat = Json.format[User]
}

class SparkMongoPlayground @Inject() (studentDistributedRepo: DistributedRepo[Student, BSONObjectID]) extends Runnable {

  override def run() {
//    val sqlContext = sparkApp.sqlContext
//    val sc = sparkApp.sc

    val students = List(Student(None, "Peter", 27), Student(None, "Paul", 34))

//    val dataFrame: DataFrame = sqlContext.createDataFrame(sc.parallelize(
//      List(Student(None, "Peter", 27), Student(None, "Paul", 34)))
//    )
//
//    saveToMongo("localhost:27017", "highschool", "students")(dataFrame)

    studentDistributedRepo.save(students)

    val studentsRDD = studentDistributedRepo.find()
    val studentsString = studentsRDD.collect.mkString(",\n")

//    val studentsRDD = loadAllFromMongo(sqlContext)("localhost:27017", "highschool", "students") // ("SELECT name, age FROM students")
//    val studentsString = studentsRDD.toJSON.collect.mkString(",\n")
//    val studentsString2 = toJson(studentsRDD).collect.mkString(",\n")
    println(studentsString)
//    println(studentsString2)
  }
}

object SparkMongoPlayground extends GuiceBuilderRunnable[SparkMongoPlayground] with App { run }