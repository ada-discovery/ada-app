package runnables.db

import com.google.inject.Inject
import org.incal.core.dataaccess.Criterion.Infix
import dataaccess._
import models.FieldTypeId
import org.apache.ignite.Ignite
import org.incal.core.dataaccess.RepoSynchronizer
import reactivemongo.bson.BSONObjectID
import org.incal.play.GuiceRunnableApp

import scala.concurrent.Await
import scala.concurrent.duration._

class TestIgniteDictionary @Inject() (ignite: Ignite, fieldRepoFactory: FieldRepoFactory) extends Runnable {

  val dataSetId = "lux_park.clinical"

  override def run = {
    val fieldRepo = RepoSynchronizer(fieldRepoFactory.apply(dataSetId), 2 minutes)

    val genderField = fieldRepo.get("cdisc_dm_sex").get
    println("Original: " + genderField)

    val repetitions = 5
    val updateStart = new java.util.Date()
    (0 until repetitions).foreach { i =>
      fieldRepo.update(genderField.copy(label = Some("Gender " + i)))

      val genderUpdated = fieldRepo.get("cdisc_dm_sex").get
      println("Updated " + genderUpdated)
    }
    println(s"$repetitions updates finished in ${new java.util.Date().getTime - updateStart.getTime} ms.")

    println("Saving a new field")
    val newGenderField = genderField.copy(name = "cdisc_dm_sex_new", label = Some("New Gender"))
    fieldRepo.save(newGenderField)

    fieldRepo.delete(newGenderField.name)

    val recruitmentGroup = fieldRepo.get("control_q1").get
    val diagGroup = fieldRepo.get("diag_control").get

    // updating bulk
    println("Going to update 3 fields.")
    fieldRepo.update(Seq(
      genderField.copy(label = Some("Gender new")),
      recruitmentGroup.copy(label = Some("Recruitment Group new")),
      diagGroup.copy(label = Some("Diag Group new"))
    ))

    println("Going them back to the original state.")
    fieldRepo.update(Seq(
      genderField,
      recruitmentGroup,
      diagGroup
    ))

    val count = fieldRepo.count(Nil)
    println(count)

//    val allFuture = fieldRepo.find(projection = Seq("fieldType"))
//    val all = Await.result(allFuture, 2 minutes)
//    println(all.map(_.toString).mkString("\n"))

    val fieldsWithCategory = fieldRepo.find(Seq("categoryId" #!= None)) // Option.empty[BSONObjectID]
    val fieldsWithCategoryCount = fieldRepo.count(Seq("categoryId" #!= None)) // Option.empty[BSONObjectID]

    println(fieldsWithCategory.size)
    println(fieldsWithCategoryCount)

//    val key = Some(BSONObjectID.parse("577e18c24500004800cdc557").get)
//    val binaryKey = ignite.binary().toBinary(key)
//    val fieldsInCategoryInFuture = fieldRepo.find(criteria = Seq("categoryId" #== key), projection = Seq("fieldType","categoryId"))

    val keys = Seq(Some(BSONObjectID.parse("577e18c24500004800cdc557").get), Some(BSONObjectID.parse("577e18c24500004800cdc55f").get))
    val criteria = Seq("categoryId" #-> keys, "fieldType" #== FieldTypeId.Enum)
    val fieldsInCategory = fieldRepo.find(criteria, projection = Seq("name", "fieldType", "isArray", "label", "categoryId"))

    val fieldsInCategoryCount = fieldRepo.count(criteria)

    println(fieldsInCategory.size)
    println(fieldsInCategoryCount)
  }
}

object TestIgniteDictionary extends GuiceRunnableApp[TestIgniteDictionary]