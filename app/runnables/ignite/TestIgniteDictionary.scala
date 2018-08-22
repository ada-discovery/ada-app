package runnables.ignite

import com.google.inject.Inject
import org.incal.core.dataaccess.Criterion.Infix
import dataaccess._
import models.FieldTypeId
import org.apache.ignite.Ignite
import reactivemongo.bson.BSONObjectID
import runnables.GuiceBuilderRunnable

import scala.concurrent.Await
import scala.concurrent.duration._

class TestIgniteDictionary @Inject() (ignite: Ignite, dictionaryFieldRepoFactory: FieldRepoFactory) extends Runnable {

  val dataSetId = "lux_park.clinical"

  override def run = {
    val fieldRepo = dictionaryFieldRepoFactory.apply(dataSetId)

    val countFuture = fieldRepo.count()
    val count = Await.result(countFuture, 2 minutes)
    println(count)

//    val allFuture = fieldRepo.find(projection = Seq("fieldType"))
//    val all = Await.result(allFuture, 2 minutes)
//    println(all.map(_.toString).mkString("\n"))

    val fieldsWithCategoryFuture = fieldRepo.find(Seq("categoryId" #!= None)) // Option.empty[BSONObjectID]
    val fieldsWithCategory = Await.result(fieldsWithCategoryFuture, 2 minutes)

    val fieldsWithCategoryCountFuture = fieldRepo.count(Seq("categoryId" #!= None)) // Option.empty[BSONObjectID]
    val fieldsWithCategoryCount = Await.result(fieldsWithCategoryCountFuture, 2 minutes)

    println(fieldsWithCategory.size)
    println(fieldsWithCategoryCount)
    println(fieldsWithCategory.map(_.toString).mkString("\n"))

//    val key = Some(BSONObjectID.parse("577e18c24500004800cdc557").get)
//    val binaryKey = ignite.binary().toBinary(key)
//    val fieldsInCategoryInFuture = fieldRepo.find(criteria = Seq("categoryId" #== key), projection = Seq("fieldType","categoryId"))
    val keys = Seq(Some(BSONObjectID.parse("577e18c24500004800cdc557").get), Some(BSONObjectID.parse("577e18c24500004800cdc55f").get))
    val criteria = Seq("categoryId" #-> keys, "fieldType" #== FieldTypeId.Enum)
    val fieldsInCategoryFuture = fieldRepo.find(criteria, projection = Seq("name", "fieldType", "isArray", "label", "categoryId"))
    val fieldsInCategory = Await.result(fieldsInCategoryFuture, 2 minutes)

    val fieldsInCategoryCountFuture = fieldRepo.count(criteria)
    val fieldsInCategoryCount = Await.result(fieldsInCategoryCountFuture, 2 minutes)

    println(fieldsInCategory.size)
    println(fieldsInCategoryCount)
    println(fieldsInCategory.map(_.toString).mkString("\n"))
  }
}

object TestIgniteDictionary extends GuiceBuilderRunnable[TestIgniteDictionary] with App { run }