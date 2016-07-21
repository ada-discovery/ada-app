package runnables

import javax.inject.Inject

import models.Student
import persistence.DistributedRepo
import reactivemongo.bson.BSONObjectID

class SparkMongoPlayground @Inject() (studentDistRepo: DistributedRepo[Student, BSONObjectID]) extends Runnable {

  override def run() {
    val students = List(Student("James", 29), Student("Caroline", 45))

    println("Saving")
    studentDistRepo.save(students)

    println("Finding all")
    val studentsDataset = studentDistRepo.find() // Some("age > 40"),(Some(Seq("name", "age"))), Some(Seq("age")))

//    val studentsDataset2 = SparkHelper.toFormatted(studentsDataset, Student.StudentFormat.reads)

//    println(studentsDataset2.count())

    println(studentsDataset.collect.mkString(",\n"))

    //     println(studentsDataFrame2.collect.mkString(",\n"))
  }
}

object SparkMongoPlayground extends GuiceBuilderRunnable[SparkMongoPlayground] with App { run }