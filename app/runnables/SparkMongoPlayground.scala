package runnables

import javax.inject.Inject

import models.Student
import persistence.{SparkHelper, DistributedRepo}
import reactivemongo.bson.BSONObjectID

class SparkMongoPlayground @Inject() (studentDistRepo: DistributedRepo[Student, BSONObjectID]) extends Runnable {

  override def run() {
    val students = List(Student("James", 29), Student("Caroline", 45))

    studentDistRepo.save(students)

    val studentsDataFrame = studentDistRepo.findJson() // Some("age > 40"),(Some(Seq("name", "age"))), Some(Seq("age")))

    val studentsDataFrame2 = SparkHelper.toFormatted(studentsDataFrame, Student.StudentFormat.reads)

    println(studentsDataFrame2.count())

    println(studentsDataFrame.collect.mkString(",\n"))

    //     println(studentsDataFrame2.collect.mkString(",\n"))
  }
}

object SparkMongoPlayground extends GuiceBuilderRunnable[SparkMongoPlayground] with App { run }